package com.leafia.passive.effects;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import org.joml.Matrix4d;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

import com.leafia.dev.custompacket.LeafiaCustomPacket;
import com.llib.technical.LeafiaEase;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;
import net.minecraft.world.phys.Vec3;

/**
 * The camera shake. Upstream: {@code com/leafia/passive/effects/LeafiaShakecam.java}, a client-only
 * class that moves the camera by calling {@code GL11.glTranslated}/{@code glRotated} from a
 * {@code CameraSetup} event.
 *
 * <h2>What changed, and why</h2>
 *
 * <b>A shake does not touch the GPU any more; it writes into a {@link ShakeFrame}.</b> 1.21.1 has no
 * matrix stack to call into from an event, so each instance's {@code render} adds its rotation or
 * translation to a {@link Matrix4d} in the same order upstream issued its GL calls (JOML's
 * {@code rotate} and {@code translate} post-multiply, exactly as GL's do), and
 * {@link LeafiaShakecamClient} applies the result: the rotation through the camera angles, the
 * translation through the modelview stack. That makes this class <b>loadable on a server</b>, which
 * is what lets a GameTest pin every number below - upstream's {@code @SideOnly(Side.CLIENT)} marked a
 * fact (it needs a player and a GL context) that this port keeps by moving those two needs into the
 * client class, not by dropping the marker (D-954's lesson).
 *
 * <p>Two further changes, both unavoidable. The noise is vanilla's {@link SimplexNoise}, one octave
 * of it, which is what 1.12.2's {@code NoiseGeneratorPerlin(random, 1)} was. And the instance list
 * is a list: upstream's {@code HashSet} iterates in identity-hash order, so two shakes at once
 * composed their (non-commuting) rotations in a different order every run; a list composes them in
 * the order they arrived.
 *
 * <h2>Public API for callers</h2>
 *
 * <pre>
 *   // server: shake everyone within 120 blocks of pos, using a preset, stronger and longer
 *   LeafiaShakecam.sendNear(level, pos, 120, "preset=EXPLOSION", "intensity*2", "duration=8");
 *   // server: one player, anywhere (no position: the shake is the same at any distance)
 *   LeafiaShakecam.send(player, null, "type=smooth", "intensity=9", "range=30");
 *   // client: build and add one directly, as the torex does
 *   LeafiaShakecam._addShake(pos, new shakeSimple(8f, Ease.BACK, Direction.I).configure(range, 24f, 0.5f, null));
 * </pre>
 *
 * The parameter strings are the ones {@code /hbmleaf shake} takes; {@link #parse} is the one place
 * that reads them. Upstream names ({@code shakeInstance}, {@code shakeSimple}, {@code shakeSmooth},
 * {@code _addShake}) are kept so the callers still to be ported need no edits.
 */
public class LeafiaShakecam {

	/** One octave of simplex noise, made on first use; upstream's listener makes it at client start. */
	@Nullable
	public static SimplexNoise noise;

	/** What the post-processing "nuclear" shader will read once the addon has one. */
	public static float blurSum = 0;
	public static float bloomSum = 0;

	public static final List<shakeInstance> instances = new ArrayList<>();

	private static SimplexNoise noise() {
		if(noise == null) noise = new SimplexNoise(RandomSource.create());
		return noise;
	}

	/**
	 * What a frame's worth of shakes adds up to: a transform in eye space, applied after the camera's
	 * own. Upstream's GL calls, recorded.
	 */
	public static final class ShakeFrame {
		public final Matrix4d matrix = new Matrix4d();

		public void translate(double x, double y, double z) {
			matrix.translate(x, y, z);
		}

		/** {@code glRotated(degrees, x, y, z)}. */
		public void rotate(double degrees, double x, double y, double z) {
			matrix.rotate(Math.toRadians(degrees), x, y, z);
		}

		public boolean isIdentity() {
			return (matrix.properties() & Matrix4d.PROPERTY_IDENTITY) != 0 || matrix.equals(new Matrix4d(), 1.0E-12);
		}

		/** The eye-space translation: the part the modelview stack applies. */
		public Vector3d translation() {
			return matrix.getTranslation(new Vector3d());
		}

		/** The eye-space rotation: the part the camera angles absorb. */
		public Quaterniond rotation() {
			return matrix.getUnnormalizedRotation(new Quaterniond()).normalize();
		}

		/**
		 * The rotation the whole frame is applied as: {@link #rotation}, with the translation
		 * folded in as the small turn that moves an object {@code depth} blocks ahead by the same
		 * amount. See {@link LeafiaShakecam#SIMPLE_SHAKE_DEPTH} for why a translation cannot be
		 * applied as one.
		 */
		public Quaterniond effectiveRotation(double depth) {
			Vector3d t = translation();
			Quaterniond fromTranslation = new Quaterniond()
					.rotateY(-Math.atan2(t.x, depth))
					.rotateX(Math.atan2(t.y, depth));
			return fromTranslation.mul(rotation());
		}
	}

	/**
	 * <b>The one approximation in the shake.</b> Upstream's {@code shakeSimple} moves the camera
	 * sideways with {@code glTranslated}, which shifts a near object more than a far one. 1.21.1 has
	 * no hook that can do that: the camera's position is set inside {@code Camera.setup} after the
	 * event, terrain is drawn with the matrix handed to it rather than the shared stack, and the
	 * only thing the event may change is the three angles. So the jitter is applied as the small
	 * turn that would shift an object this many blocks ahead by the same distance. At the default
	 * intensity of 4 the offset is at most 0.16 blocks, about 1 degree; at the torex's 24 it is
	 * 0.96, about 7 degrees, next to the smooth shakes' tens. Exact translation would need an
	 * access transformer on {@code Camera.setPosition} and a hook at the right moment, or a
	 * mixin; a hand-off (D-1089).
	 */
	public static final double SIMPLE_SHAKE_DEPTH = 8.0;

	/**
	 * The camera angles {@code (yaw, pitch, roll)}, in degrees, after {@code frame} is applied to
	 * them. The vanilla camera's rotation is {@code rotationYXZ(PI - yaw, -pitch, -roll)}; the
	 * frame is an eye-space transform applied after it, so the new rotation is the camera's times
	 * the frame's inverse, read back as the same three angles.
	 */
	public static float[] cameraAngles(float yaw, float pitch, float roll, ShakeFrame frame) {
		Quaternionf camera = new Quaternionf().rotationYXZ(
				(float) Math.PI - yaw * (float) (Math.PI / 180.0),
				-pitch * (float) (Math.PI / 180.0),
				-roll * (float) (Math.PI / 180.0));
		Quaterniond s = frame.effectiveRotation(SIMPLE_SHAKE_DEPTH);
		Quaternionf shaken = camera.mul(new Quaternionf((float) -s.x, (float) -s.y, (float) -s.z, (float) s.w), new Quaternionf());
		Vector3f euler = shaken.getEulerAnglesYXZ(new Vector3f());
		return new float[] {
				((float) Math.PI - euler.y) * (float) (180.0 / Math.PI),
				-euler.x * (float) (180.0 / Math.PI),
				-euler.z * (float) (180.0 / Math.PI)
		};
	}

	public static void localTick() {
		for(int i = 0; i < instances.size();) {
			shakeInstance instance = instances.get(i);
			if(!instance.tick()) instances.remove(i);
			else i++;
		}
	}

	/**
	 * One frame's {@code CameraSetup}: every live shake writes into {@code frame}, and the blur and
	 * bloom sums are recomputed from what each returned.
	 *
	 * @param playerPos where the local player is, for the distance falloff
	 */
	public static void shakeCam(Vec3 playerPos, ShakeFrame frame) {
		double sumBlur = 0;
		double sumBloom = 0;
		for(shakeInstance instance : instances) {
			double dist = 0;
			if(instance.position != null) {
				dist = Math.sqrt(instance.position.distToCenterSqr(playerPos.x, playerPos.y, playerPos.z));
			}
			double n = instance.render(dist, frame);

			sumBlur += (Math.pow(n / instance.blurDulling, instance.blurExponent) * 0.005);
			sumBloom += (Math.pow(n / instance.bloomDulling / 5, instance.bloomExponent) * 0.25);
		}
		blurSum = (float) sumBlur;
		bloomSum = (float) sumBloom;
	}

	public static void _addShake(@Nullable BlockPos pos, shakeInstance instance) {
		if(pos != null) instance.position = pos;
		instances.add(instance);
	}

	public static class shakeInstance {
		public BlockPos position = null;
		public float range = 25;
		public float intensity = 4;
		public float curve = 2;
		public float speed = 4;
		public float duration = 5;
		public double blurExponent = 2;
		public double blurDulling = 16;
		public double bloomExponent = 6;
		public double bloomDulling = 16;
		public LeafiaEase.Ease ease = LeafiaEase.Ease.EXPO;
		public LeafiaEase.Direction direction = LeafiaEase.Direction.O;
		public LeafiaEase easeInstance;

		shakeInstance(@Nullable Float duration, @Nullable LeafiaEase.Ease ease, @Nullable LeafiaEase.Direction direction) {
			if(duration != null) this.duration = duration;
			if(ease != null) this.ease = ease;
			if(direction != null) this.direction = direction;
			easeInstance = new LeafiaEase(this.ease, this.direction);
		}

		public shakeInstance configure(@Nullable Float range, @Nullable Float intensity, @Nullable Float curve, @Nullable Float speed) {
			if(range != null) this.range = range;
			if(intensity != null) this.intensity = intensity;
			if(curve != null) this.curve = curve;
			if(speed != null) this.speed = speed;
			return this;
		}

		public shakeInstance setRange(float range) {
			this.range = range;
			return this;
		}

		public shakeInstance setIntensity(float intensity) {
			this.intensity = intensity;
			return this;
		}

		public shakeInstance setCurve(float curve) {
			this.curve = curve;
			return this;
		}

		public shakeInstance setSpeed(float speed) {
			this.speed = speed;
			return this;
		}

		public shakeInstance loadPreset(Preset preset) {
			preset.callback.accept(this);
			return this;
		}

		/**
		 * One at the start, falling to zero over {@code duration}. Follows {@link #ease}
		 * since D-3075 ({@code LeafiaEase.get(t, min, max, clamp)} used to ignore the curve, D-1004).
		 */
		public double getTimeMultiplier(double time) {
			if(easeInstance == null) return 1;
			easeInstance.updateEase(ease, direction);
			return 1 - easeInstance.get(time / duration, 0, 1, true);
		}

		public double getCalculatedIntensity(double distance) {
			return intensity * Math.pow(Mth.clamp(1 - distance / range, 0, 1), curve);
		}

		/** Once per client tick; false when the shake is over. */
		public boolean tick() {
			return false;
		}

		/** Once per frame: writes into {@code frame}, returns how strong the shake is (the shader's input). */
		public double render(double distance, ShakeFrame frame) {
			return 0;
		}
	}

	public enum Preset {
		RUPTURE(25, 8, 2, 8),
		EXPLOSION(62.5f, 12, 3, 8),
		QUAKE(350, 4, 3, 4),
		PWR_NEAR(self -> {
			self.intensity *= 2;
			self.speed *= 2;
			self.duration /= 1.5;
			self.bloomDulling *= 0.75;
		}, "i*2 s*2 d/1.5 bloomD*0.75"),
		PWR_FAR(self -> {
			self.intensity /= 2;
			self.speed /= 2;
			self.blurDulling *= 2;
		}, "i/2 s/2 blurD*2");

		public final Consumer<shakeInstance> callback;
		public final String paramString;

		Preset(float range, float intensity, float curve, float speed) {
			paramString = String.format("r=%1.1f i=%1.1f c=%1.1f s=%1.1f", range, intensity, curve, speed);
			this.callback = self -> {
				self.range = range;
				self.intensity = intensity;
				self.curve = curve;
				self.speed = speed;
			};
		}

		Preset(Consumer<shakeInstance> callback, String params) {
			this.paramString = params;
			this.callback = callback;
		}
	}

	/** A jitter: the camera jumps by a random offset every tick. */
	public static class shakeSimple extends shakeInstance {
		public int ticks = 0;
		float x = 0;
		float y = 0;
		RandomSource rand;

		public shakeSimple(@Nullable Float duration, @Nullable LeafiaEase.Ease ease, @Nullable LeafiaEase.Direction direction) {
			super(duration, ease, direction);
			rand = RandomSource.create();
		}

		@Override
		public boolean tick() {
			ticks++;
			x = rand.nextFloat() - 0.5f;
			y = rand.nextFloat() - 0.5f;
			return !(ticks / 20d >= duration);
		}

		@Override
		public double render(double distance, ShakeFrame frame) {
			frame.translate(
					0.04 * x * getCalculatedIntensity(distance) * getTimeMultiplier(ticks / 20d) * 2,
					0.04 * y * getCalculatedIntensity(distance) * getTimeMultiplier(ticks / 20d) * 2,
					0);
			return getCalculatedIntensity(distance) * getTimeMultiplier(ticks / 20d) * 4;
		}
	}

	/**
	 * A sway: the camera rotates about each axis by a noise function of <b>real time</b>, not of
	 * ticks, so a shake runs at the same pace at any frame rate and keeps running while the game is
	 * lagging. The timer is {@code floorMod(currentTimeMillis, 10000)} differences, upstream's way of
	 * reading a frame delta without a field that could wrap.
	 */
	public static class shakeSmooth extends shakeInstance {
		long lastMil = 0;
		public double timer = 0;

		public shakeSmooth(@Nullable Float duration, @Nullable LeafiaEase.Ease ease, @Nullable LeafiaEase.Direction direction) {
			super(duration, ease, direction);
			lastMil = Math.floorMod(System.currentTimeMillis(), 10000);
		}

		@Override
		public boolean tick() {
			return !(timer >= duration);
		}

		@Override
		public double render(double distance, ShakeFrame frame) {
			return renderAt(distance, frame, System.currentTimeMillis());
		}

		/** Sets the reference time the next frame's delta is measured from. For tests, which cannot wait. */
		public shakeSmooth startClock(long nowMillis) {
			lastMil = Math.floorMod(nowMillis, 10000);
			return this;
		}

		/** {@link #render} with the clock passed in, so a test can drive it. */
		public double renderAt(double distance, ShakeFrame frame, long nowMillis) {
			long curMil = Math.floorMod(nowMillis, 10000);
			timer = timer + Math.floorMod(curMil - lastMil, 10000) / 1e+3;
			lastMil = curMil;
			SimplexNoise n = noise();
			frame.rotate(n.getValue(timer * speed, 0) * getCalculatedIntensity(distance) * getTimeMultiplier(timer), 1, 0, 0);
			frame.rotate(n.getValue(0, timer * speed) * getCalculatedIntensity(distance) * getTimeMultiplier(timer), 0, 1, 0);
			frame.rotate(n.getValue(timer * speed, timer * speed) * getCalculatedIntensity(distance) * getTimeMultiplier(timer), 0, 0, 1);
			return getCalculatedIntensity(distance) * getTimeMultiplier(timer) * speed;
		}
	}

	// ----- parameters: /hbmleaf shake's own language ------------------------------------------

	/** The nine numeric parameters, in the order {@link #parse} indexes them. */
	public static final String[] NUMERICS = {"range", "intensity", "curve", "speed", "duration", "blurDulling", "blurExponent", "bloomDulling", "bloomExponent"};

	/** What {@link #parse} made: a shake (null if the type was invalid) and every complaint it had. */
	public record Parsed(@Nullable shakeInstance shake, List<String> warnings) {}

	private static double parseDouble(String s) {
		double v;
		try {
			v = Double.parseDouble(s);
		} catch(NumberFormatException e) {
			throw new IllegalArgumentException("'" + s + "' is not a valid number");
		}
		if(Double.isNaN(v) || Double.isInfinite(v)) throw new IllegalArgumentException("'" + s + "' is not a valid number");
		return v;
	}

	/**
	 * Reads {@code /hbmleaf shake}'s parameters, as upstream's {@code ShakecamPacket.Handler} does on
	 * the client. Each is {@code name=value} (set), {@code name+value}, {@code name-value},
	 * {@code name*value} or {@code name/value} (adjust the value the type or preset gave), plus
	 * {@code type=simple|smooth}, {@code preset=NAME} and {@code ease=<name>|none}. Order of
	 * application, as upstream: set, then the preset, then the explicit sets, then multiply, then add.
	 * A bad parameter is a warning, not a failure; only a bad {@code type} gives no shake.
	 */
	public static Parsed parse(String[] args) {
		List<String> warnings = new ArrayList<>();
		Float[] params = {null, null, null, null, null, null, null, null, null};
		float[] adds = {0, 0, 0, 0, 0, 0, 0, 0, 0};
		float[] multipliers = {1, 1, 1, 1, 1, 1, 1, 1, 1};
		LeafiaEase.Ease ease = null;
		LeafiaEase.Direction direction = null;
		String type = "simple";
		Preset preset = null;
		boolean removeEase = false;
		for(String arg : args) {
			if(arg.startsWith("type=")) type = arg.substring(5);
			for(int i = 0; i < NUMERICS.length; i++) {
				String s = NUMERICS[i];
				if(arg.startsWith(s)) {
					arg = arg.substring(s.length());
					if(arg.length() >= 2) {
						String op = arg.substring(0, 1);
						arg = arg.substring(1);
						try {
							switch(op) {
							case "=" -> params[i] = (float) parseDouble(arg);
							case "+" -> adds[i] = (float) parseDouble(arg);
							case "-" -> adds[i] = -(float) parseDouble(arg);
							case "*" -> multipliers[i] = (float) parseDouble(arg);
							case "/" -> multipliers[i] = 1 / (float) parseDouble(arg);
							default -> warnings.add("Invalid operator: " + op);
							}
						} catch(IllegalArgumentException e) {
							warnings.add(e.getMessage());
						}
					} else {
						warnings.add("Malformed numeric parameter!");
					}
				}
			}
			if(arg.startsWith("ease=")) {
				arg = arg.substring(5);
				if(arg.equals("none")) removeEase = true;
				else {
					try {
						LeafiaEase easeInsta = LeafiaEase.parseEase(arg);
						ease = easeInsta.ease;
						direction = easeInsta.dir;
					} catch(IllegalArgumentException e) {
						warnings.add(e.getMessage());
					}
				}
			}
			if(arg.startsWith("preset=")) {
				try {
					preset = Preset.valueOf(arg.substring(7));
				} catch(IllegalArgumentException e) {
					warnings.add("Invalid preset: " + arg.substring(7));
				}
			}
		}
		shakeInstance shake;
		switch(type) {
		case "simple" -> shake = new shakeSimple(params[4], ease, direction);
		case "smooth" -> shake = new shakeSmooth(params[4], ease, direction);
		default -> {
			warnings.add("Invalid type: " + type);
			return new Parsed(null, warnings);
		}
		}
		if(preset != null) shake.loadPreset(preset);
		shake.configure(params[0], params[1], params[2], params[3]);
		if(params[5] != null) shake.blurDulling = params[5];
		if(params[6] != null) shake.blurExponent = params[6];
		if(params[7] != null) shake.bloomDulling = params[7];
		if(params[8] != null) shake.bloomExponent = params[8];

		shake.range *= multipliers[0];
		shake.intensity *= multipliers[1];
		shake.curve *= multipliers[2];
		shake.speed *= multipliers[3];
		shake.duration *= multipliers[4];
		shake.blurDulling *= multipliers[5];
		shake.blurExponent *= multipliers[6];
		shake.bloomDulling *= multipliers[7];
		shake.bloomExponent *= multipliers[8];

		shake.range += adds[0];
		shake.intensity += adds[1];
		shake.curve += adds[2];
		shake.speed += adds[3];
		shake.duration += adds[4];
		shake.blurDulling += adds[5];
		shake.blurExponent += adds[6];
		shake.bloomDulling += adds[7];
		shake.bloomExponent += adds[8];

		if(removeEase) shake.easeInstance = null;
		return new Parsed(shake, warnings);
	}

	// ----- sending ----------------------------------------------------------------------------

	/**
	 * Server to client: shake {@code player}'s camera. {@code pos} is where the shake comes from
	 * (the player's distance to it weakens it); null means it is the same wherever they are.
	 */
	public static void send(ServerPlayer player, @Nullable BlockPos pos, String... params) {
		LeafiaCustomPacket wire = LeafiaCustomPacket.__start(new ShakecamPacket(params, pos));
		if(wire != null && player.connection != null && player.connection.hasChannel(LeafiaCustomPacket.TYPE)) wire.__sendToClient(player);
	}

	/** The players {@link #sendNear} would reach: within {@code playerRange} blocks of the centre of {@code pos}. */
	public static List<ServerPlayer> playersNear(ServerLevel level, BlockPos pos, double playerRange) {
		double rangeSq = playerRange * playerRange;
		List<ServerPlayer> out = new ArrayList<>();
		for(ServerPlayer player : level.players()) {
			if(player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= rangeSq) out.add(player);
		}
		return out;
	}

	/** Shakes every player within {@code playerRange} blocks of {@code pos}, as a shake from {@code pos}. */
	public static void sendNear(ServerLevel level, BlockPos pos, double playerRange, String... params) {
		LeafiaCustomPacket wire = LeafiaCustomPacket.__start(new ShakecamPacket(params, pos));
		if(wire == null) return;
		for(ServerPlayer player : playersNear(level, pos, playerRange)) {
			if(player.connection != null && player.connection.hasChannel(LeafiaCustomPacket.TYPE)) wire.__sendToClient(player);
		}
	}

	/**
	 * Server to client: a shake, as the parameter strings {@code /hbmleaf shake} takes, plus an
	 * optional source position. Upstream's {@code CommandLeaf.ShakecamPacket}, moved here so the
	 * shake and its wire are in one file; the bytes are the same: a count, the strings, then three
	 * ints if there is a position.
	 */
	public static class ShakecamPacket implements com.leafia.dev.custompacket.LeafiaCustomPacketEncoder {
		public String[] params = new String[0];
		@Nullable
		public BlockPos pos = null;

		public ShakecamPacket() { }

		public ShakecamPacket(String[] args, @Nullable BlockPos pos) {
			this.params = args;
			this.pos = pos;
		}

		@Override
		public void encode(com.leafia.dev.optimization.bitbyte.LeafiaBuf buf) {
			buf.writeByte(params.length);
			for(String param : params) buf.writeUTF8String(param);
			if(pos != null) {
				buf.writeInt(pos.getX());
				buf.writeInt(pos.getY());
				buf.writeInt(pos.getZ());
			}
		}

		public static ShakecamPacket read(com.leafia.dev.optimization.bitbyte.LeafiaBuf buf) {
			String[] read = new String[buf.readByte() & 0xFF];
			for(int i = 0; i < read.length; i++) read[i] = buf.readUTF8String();
			BlockPos readPos = null;
			if(buf.readableBits() >= 32 * 3) readPos = new BlockPos(buf.readInt(), buf.readInt(), buf.readInt());
			return new ShakecamPacket(read, readPos);
		}

		@Override
		public Consumer<net.neoforged.neoforge.network.handling.IPayloadContext> decode(com.leafia.dev.optimization.bitbyte.LeafiaBuf buf) {
			ShakecamPacket p = read(buf);
			return ctx -> {
				if(ctx.flow() == net.minecraft.network.protocol.PacketFlow.CLIENTBOUND && net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) ClientOnly.receive(p.params, p.pos);
			};
		}

		@net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
		private static final class ClientOnly {
			static void receive(String[] params, @Nullable BlockPos pos) {
				LeafiaShakecamClient.receive(params, pos);
			}
		}
	}
}
