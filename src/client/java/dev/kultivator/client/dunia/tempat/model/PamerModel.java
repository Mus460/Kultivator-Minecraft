package dev.kultivator.client.dunia.tempat.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.kultivator.Kultivator;
import dev.kultivator.client.epic.EpicBudget;
import dev.kultivator.client.epic.ShaderCompat;
import dev.kultivator.client.epic.kosmik.Kosmik3D;
import dev.kultivator.client.sosok.gerak.PropKatalog;
import dev.kultivator.sosok.PropId;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The model showcase of DESIGN 38.O.5 on the client ({@code /hiasan pamer} and {@code /hiasan prop}): nothing is spawned,
 * sent or changed, the models are drawn where they would stand. It shows the real renderer models and textures with
 * their real animations driven by a scripted clock, so a tester sees every 38.O.2 creature and mechanism and every hand
 * prop without finding them in the world first.
 * <ul>
 * <li><b>pamer</b> (30 s): an arc of nine stands in front of the player, each with its name: the Penjaga Kristal
 * (hover, heavy walk, two-fisted hammer blow, glow), the tomb puppet (rises from its seat, stiff walk, three-key cut), the
 * spirit rat (scurrying, glowing eyes), the cage door (lock drops, door swings), the glyph plate (quarter turns through
 * the six elements, the glyph burns when "solved"), the false cliff door (slides aside), the caravan cart (rolling
 * wheels, the three loads in turn), the ferry raft (bobbing, poling strokes) and the notice board (sheets pasted one by
 * one, fluttering).</li>
 * <li><b>prop</b> (30 s): a wall of the hand props, page 1 = DESIGN 38.O.4 props 1-40, page 2 = themed-city and
 * appearance props 41-88, each turning slowly over its number and name ({@link PropKatalog}, the real item models).</li>
 * </ul>
 * Budget: one showcase at a time, drawn only within 48 blocks, nothing at tier −1, in a shadow pass or under a Film
 * Kosmik stage; light is sampled once a second per stand. Models are baked on first use, item stacks come from
 * {@link PropKatalog#stack} (made lazily).
 */
final class PamerModel {

	private static final int DURASI = 600;
	private static final double JARAK = 48.0D;
	private static final String K = "kultivator.tempat_model.pamer.";

	private enum Jenis {
		KRISTAL, BONEKA, TIKUS, SANGKAR, GLIF, PINTU, GEROBAK, RAKIT, PAPAN
	}

	/** One stand: a model or a prop at a world point, facing {@code yaw}. */
	private static final class Stand {
		final double x;
		final double y;
		final double z;
		final float yaw;
		final Jenis jenis;
		final int prop;
		int cahaya = LightCoordsUtil.FULL_SKY;
		long cahayaTick = Long.MIN_VALUE;

		Stand(double x, double y, double z, float yaw, Jenis jenis, int prop) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.yaw = yaw;
			this.jenis = jenis;
			this.prop = prop;
		}
	}

	private static final List<Stand> STAND = new ArrayList<>();
	private static long sampai = Long.MIN_VALUE;
	private static long mulai;
	private static boolean initialised;
	private static boolean warned;

	// models and states, baked on first use (render thread)
	private static PenjagaKristalRenderer.Model kristal;
	private static BonekaPenjagaRenderer.Model boneka;
	private static TikusRohRenderer.Model tikus;
	private static SangkarRenderer.Model sangkar;
	private static LempengGlifRenderer.Model glif;
	private static PintuTebingRenderer.Model pintu;
	private static GerobakRenderer.Model gerobak;
	private static RakitRenderer.Model rakit;
	private static PapanHidupRenderer.Model papan;
	private static final PenjagaKristalRenderer.State S_KRISTAL = new PenjagaKristalRenderer.State();
	private static final BonekaPenjagaRenderer.State S_BONEKA = new BonekaPenjagaRenderer.State();
	private static final LivingEntityRenderState S_TIKUS = new LivingEntityRenderState();
	private static final SangkarRenderer.State S_SANGKAR = new SangkarRenderer.State();
	private static final LempengGlifRenderer.State S_GLIF = new LempengGlifRenderer.State();
	private static final PintuTebingRenderer.State S_PINTU = new PintuTebingRenderer.State();
	private static final GerobakRenderer.State S_GEROBAK = new GerobakRenderer.State();
	private static final RakitRenderer.State S_RAKIT = new RakitRenderer.State();
	private static final PapanHidupRenderer.State S_PAPAN = new PapanHidupRenderer.State();
	/** One item render state per prop index (the submitted layers keep referring to their state within a frame). */
	private static final ItemStackRenderState[] ITEM = new ItemStackRenderState[PropId.SOSOK_LAST + 1];

	static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		LevelRenderEvents.COLLECT_SUBMITS.register(PamerModel::collect);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> hapus());
	}

	/** The creature and mechanism arc, 30 s. */
	static void pamer(ClientLevel level, Vec3 pusat, float yawDeg) {
		STAND.clear();
		float yr = yawDeg * Mth.DEG_TO_RAD;
		Jenis[] urut = Jenis.values();
		for (int i = 0; i < urut.length; i++) {
			// an arc of radius 7 spanning 160°, every stand facing the player
			double a = yr + Math.toRadians(-80.0D + 160.0D * i / (urut.length - 1));
			double x = pusat.x - Math.sin(a) * 7.0D;
			double z = pusat.z + Math.cos(a) * 7.0D;
			// entity yaw convention: forward = (-sin, cos); the stand's front looks back at the player
			float face = (float) Math.toDegrees(Math.atan2(-(pusat.x - x), pusat.z - z));
			STAND.add(new Stand(x, Math.floor(pusat.y), z, face, urut[i], 0));
		}
		mulai = level.getGameTime();
		sampai = mulai + DURASI;
	}

	/** The prop wall, page 1 (1-40) or 2 (41-88), 30 s. */
	static int prop(ClientLevel level, Vec3 pusat, float yawDeg, int halaman) {
		STAND.clear();
		int from = halaman <= 1 ? PropId.TEMPAT_FIRST : PropId.TEMA_FIRST;
		int to = halaman <= 1 ? PropId.TEMPAT_LAST : PropId.SOSOK_LAST_DIPAKAI;
		float yr = yawDeg * Mth.DEG_TO_RAD;
		double fx = -Mth.sin(yr);
		double fz = Mth.cos(yr);
		double sx = -fz;
		double sz = fx;
		int cols = 10;
		int n = 0;
		for (int id = from; id <= to; id++) {
			if (!PropKatalog.ada(id)) {
				continue;
			}
			int col = n % cols;
			int row = n / cols;
			double side = (col - (cols - 1) / 2.0D) * 1.0D;
			double x = pusat.x + fx * 5.0D + sx * side;
			double z = pusat.z + fz * 5.0D + sz * side;
			STAND.add(new Stand(x, Math.floor(pusat.y) + 3.6D - row * 1.15D, z, yawDeg + 180.0F, null, id));
			n++;
		}
		mulai = level.getGameTime();
		sampai = mulai + DURASI;
		return n;
	}

	static void hapus() {
		STAND.clear();
		sampai = Long.MIN_VALUE;
	}

	/** Stands shown now (the {@code /hiasan info} report). */
	static int aktif() {
		return STAND.size();
	}

	// ================================================================ frame

	private static void collect(LevelRenderContext context) {
		if (STAND.isEmpty()) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null) {
			return;
		}
		long game = level.getGameTime();
		if (game > sampai) {
			hapus();
			return;
		}
		if (ShaderCompat.shadowPass() || Kosmik3D.covering()) {
			return;
		}
		long start = System.nanoTime();
		try {
			EpicBudget.beginFrame();
			if (EpicBudget.tier() < 0) {
				return;
			}
			bake(minecraft.getEntityModels());
			CameraRenderState camera = context.levelState().cameraRenderState;
			float pt = camera.cameraEntityPartialTicks;
			float t = (game - mulai) + pt;
			SubmitNodeCollector collector = context.submitNodeCollector();
			PoseStack stack = context.poseStack();
			for (Stand s : STAND) {
				float dx = (float) (s.x - camera.pos.x);
				float dy = (float) (s.y - camera.pos.y);
				float dz = (float) (s.z - camera.pos.z);
				if (dx * dx + dy * dy + dz * dz > JARAK * JARAK) {
					continue;
				}
				if (game - s.cahayaTick >= 20L) {
					s.cahayaTick = game;
					BlockPos p = BlockPos.containing(s.x, s.y + 1.0D, s.z);
					s.cahaya = LightCoordsUtil.pack(level.getBrightness(LightLayer.BLOCK, p), level.getBrightness(LightLayer.SKY, p));
				}
				if (s.jenis != null) {
					model(s, stack, collector, camera, dx, dy, dz, t);
				} else {
					prop(minecraft, level, s, stack, collector, camera, dx, dy, dz, t);
				}
			}
		} catch (RuntimeException exception) {
			if (!warned) {
				warned = true;
				Kultivator.LOGGER.warn("Kultivator: model showcase skipped a frame", exception);
			}
		} finally {
			EpicBudget.addCpu(System.nanoTime() - start);
		}
	}

	private static void bake(EntityModelSet set) {
		if (kristal != null) {
			return;
		}
		kristal = new PenjagaKristalRenderer.Model(set.bakeLayer(PenjagaKristalRenderer.LAYER));
		boneka = new BonekaPenjagaRenderer.Model(set.bakeLayer(BonekaPenjagaRenderer.LAYER));
		tikus = new TikusRohRenderer.Model(set.bakeLayer(TikusRohRenderer.LAYER));
		sangkar = new SangkarRenderer.Model(set.bakeLayer(SangkarRenderer.LAYER));
		glif = new LempengGlifRenderer.Model(set.bakeLayer(LempengGlifRenderer.LAYER));
		pintu = new PintuTebingRenderer.Model(set.bakeLayer(PintuTebingRenderer.LAYER));
		gerobak = new GerobakRenderer.Model(set.bakeLayer(GerobakRenderer.LAYER));
		rakit = new RakitRenderer.Model(set.bakeLayer(RakitRenderer.LAYER));
		papan = new PapanHidupRenderer.Model(set.bakeLayer(PapanHidupRenderer.LAYER));
	}

	/** One creature or mechanism with its scripted animation, its name floating above it. */
	private static void model(Stand s, PoseStack stack, SubmitNodeCollector collector, CameraRenderState camera, float dx, float dy, float dz,
			float t) {
		int light = s.cahaya;
		float labelY;
		stack.pushPose();
		stack.translate(dx, dy, dz);
		switch (s.jenis) {
			case KRISTAL -> {
				// 8 s loop: 2 s idle hover, 3 s heavy walk, 1 s hammer blow, 2 s idle
				float c = t % 160.0F;
				hidup(S_KRISTAL, t, c >= 40.0F && c < 100.0F ? (c - 40.0F) * 0.35F : 0.0F, c >= 40.0F && c < 100.0F ? 1.0F : 0.0F);
				S_KRISTAL.attack = c >= 100.0F && c < 120.0F ? (c - 100.0F) / 20.0F : 0.0F;
				submitMob(kristal, S_KRISTAL, stack, collector, PenjagaKristalRenderer.TEXTURE, PenjagaKristalRenderer.GLOW,
						0.65F + 0.35F * Mth.sin(t * 0.08F), s.yaw, 1.1F, light);
				labelY = 3.0F;
			}
			case BONEKA -> {
				// 8 s loop: rises from its seat (30 ticks), stands, walks, cuts
				float c = t % 160.0F;
				hidup(S_BONEKA, t, c >= 50.0F && c < 110.0F ? (c - 50.0F) * 0.3F : 0.0F, c >= 50.0F && c < 110.0F ? 1.0F : 0.0F);
				S_BONEKA.rise = Mth.clamp((c - 5.0F) / 30.0F, 0.0F, 1.0F);
				S_BONEKA.attack = c >= 115.0F && c < 135.0F ? (c - 115.0F) / 20.0F : 0.0F;
				submitMob(boneka, S_BONEKA, stack, collector, BonekaPenjagaRenderer.TEXTURE, null, 0.0F, s.yaw, 1.0F, light);
				labelY = 2.4F;
			}
			case TIKUS -> {
				float c = t % 80.0F;
				boolean lari = c < 50.0F;
				hidup(S_TIKUS, t, lari ? c * 0.9F : 0.0F, lari ? 1.0F : 0.0F);
				submitMob(tikus, S_TIKUS, stack, collector, TikusRohRenderer.TEXTURE, TikusRohRenderer.GLOW, 1.0F, s.yaw, 1.6F, light);
				labelY = 1.2F;
			}
			case SANGKAR -> {
				float c = t % 120.0F;
				benda(S_SANGKAR, t, s.yaw);
				S_SANGKAR.open = c < 30.0F ? 0.0F : Math.min(1.0F, (c - 30.0F) / 30.0F);
				submitBenda(sangkar, S_SANGKAR, stack, collector, SangkarRenderer.TEXTURE, null, light);
				labelY = 2.2F;
			}
			case GLIF -> {
				// a quarter turn every 30 ticks (easeOutBack over 10), the glyph changes each turn, the 6th turn "solves" it
				int turn = Mth.floor(t / 30.0F);
				float k = Math.min(1.0F, (t - turn * 30.0F) / 10.0F);
				benda(S_GLIF, t, 0.0F);
				S_GLIF.yaw = 0.0F;
				S_GLIF.turn = (turn % 4) + BendaRenderer.backOut(k) - 1.0F;
				S_GLIF.glyph = Math.floorMod(turn, 6);
				float lit = turn % 6 == 5 ? Math.min(1.0F, (t - turn * 30.0F) / 20.0F) : 0.0F;
				S_GLIF.pancar = lit * (0.8F + 0.2F * Mth.sin(t * 0.15F));
				stack.translate(0.0F, 0.6F, 0.0F);
				submitBenda(glif, S_GLIF, stack, collector, LempengGlifRenderer.TEXTURE, LempengGlifRenderer.GLOW, light);
				labelY = 1.0F;
			}
			case PINTU -> {
				float c = t % 120.0F;
				benda(S_PINTU, t, s.yaw);
				S_PINTU.slide = c < 30.0F ? 0.0F : (c < 54.0F ? (c - 30.0F) / 24.0F : (c < 96.0F ? 1.0F : 1.0F - (c - 96.0F) / 24.0F));
				submitBenda(pintu, S_PINTU, stack, collector, PintuTebingRenderer.TEXTURE, null, light);
				labelY = 2.6F;
			}
			case GEROBAK -> {
				benda(S_GEROBAK, t, s.yaw);
				S_GEROBAK.wheel = t * 0.08F;
				S_GEROBAK.rolling = 1.0F;
				S_GEROBAK.load = Mth.floor(t / 100.0F) % 3;
				submitBenda(gerobak, S_GEROBAK, stack, collector, GerobakRenderer.TEXTURE, null, light);
				labelY = 2.2F;
			}
			case RAKIT -> {
				benda(S_RAKIT, t, s.yaw);
				S_RAKIT.pole = (t % 160.0F) < 80.0F ? (t % 20.0F) / 20.0F : -1.0F;
				submitBenda(rakit, S_RAKIT, stack, collector, RakitRenderer.TEXTURE, null, light);
				labelY = 1.6F;
			}
			default -> {
				// sheets pasted one by one (6 ticks each), fluttering in front of the viewer
				float c = t % 200.0F;
				benda(S_PAPAN, t, s.yaw);
				int sheets = Math.min(PapanHidupRenderer.MAX_SHEETS, 1 + Mth.floor(c / 30.0F));
				S_PAPAN.sheets = sheets;
				S_PAPAN.paste = Math.min(1.0F, (c - (sheets - 1) * 30.0F) / 6.0F);
				S_PAPAN.near = true;
				submitBenda(papan, S_PAPAN, stack, collector, PapanHidupRenderer.TEXTURE, null, light);
				labelY = 2.6F;
			}
		}
		stack.popPose();
		label(stack, collector, camera, Component.translatable(K + s.jenis.name().toLowerCase(java.util.Locale.ROOT)), dx, dy + labelY, dz, 0.28F);
	}

	private static void hidup(LivingEntityRenderState state, float t, float walkPos, float walkSpeed) {
		state.ageInTicks = t;
		state.walkAnimationPos = walkPos;
		state.walkAnimationSpeed = walkSpeed;
		state.deathTime = 0.0F;
		state.isInvisible = false;
	}

	private static void benda(BendaRenderer.State state, float t, float yaw) {
		state.ageInTicks = t;
		state.yaw = yaw;
		state.pancar = 0.0F;
		state.isInvisible = false;
	}

	/** Like {@code LivingEntityRenderer.submit}: turn to the body yaw, scale, flip to model space, then the glow pass. */
	private static <S extends LivingEntityRenderState> void submitMob(EntityModel<S> model, S state, PoseStack stack, SubmitNodeCollector collector,
			Identifier texture, Identifier glow, float glowStrength, float yaw, float scale, int light) {
		state.bodyRot = yaw;
		stack.pushPose();
		stack.rotateDegrees(Axis.YP, 180.0F - yaw);
		stack.scale(scale, scale, scale);
		stack.scale(-1.0F, -1.0F, 1.0F);
		stack.translate(0.0F, -1.501F, 0.0F);
		collector.submitModel(model, state, stack, RenderTypes.entityCutout(texture), light, OverlayTexture.NO_OVERLAY, -1, null, 0);
		if (glow != null && glowStrength > 0.02F) {
			collector.order(1).submitModel(model, state, stack, RenderTypes.eyes(glow), light, OverlayTexture.NO_OVERLAY,
					ARGB.color(Math.round(255.0F * Math.min(1.0F, glowStrength)), 0xFFFFFF), null, 0);
		}
		stack.popPose();
	}

	/** Like {@link BendaRenderer#submit}: yaw, model space, the emissive pass when {@code pancar} is up. */
	private static <S extends BendaRenderer.State> void submitBenda(EntityModel<S> model, S state, PoseStack stack, SubmitNodeCollector collector,
			Identifier texture, Identifier glow, int light) {
		stack.pushPose();
		stack.rotateDegrees(Axis.YP, 180.0F - state.yaw);
		stack.scale(-1.0F, -1.0F, 1.0F);
		stack.translate(0.0F, -1.501F, 0.0F);
		RenderType type = RenderTypes.entityCutout(texture);
		collector.submitModel(model, state, stack, type, light, OverlayTexture.NO_OVERLAY, -1, null, 0);
		if (glow != null && state.pancar > 0.02F) {
			collector.order(1).submitModel(model, state, stack, RenderTypes.eyes(glow), light, OverlayTexture.NO_OVERLAY,
					ARGB.color(Math.round(255.0F * Math.min(1.0F, state.pancar)), 0xFFFFFF), null, 0);
		}
		stack.popPose();
	}

	/** One prop turning over its number and name. */
	private static void prop(Minecraft minecraft, ClientLevel level, Stand s, PoseStack stack, SubmitNodeCollector collector, CameraRenderState camera,
			float dx, float dy, float dz, float t) {
		ItemStack item = PropKatalog.stack(s.prop);
		if (item == null) {
			return;
		}
		if (s.prop >= ITEM.length) {
			return;
		}
		ItemStackRenderState state = ITEM[s.prop];
		if (state == null) {
			state = new ItemStackRenderState();
			ITEM[s.prop] = state;
		}
		minecraft.getItemModelResolver().updateForTopItem(state, item, ItemDisplayContext.NONE, level, null, 0);
		if (state.isEmpty()) {
			return;
		}
		PropKatalog.Pegangan grip = PropKatalog.tangan(s.prop);
		float scale = grip.panjang() ? 0.42F : (grip.besar() ? 0.6F : 0.85F);
		stack.pushPose();
		stack.translate(dx, dy + 0.55F, dz);
		stack.rotateDegrees(Axis.YP, -s.yaw + t * 1.5F + s.prop * 23.0F);
		stack.scale(scale, scale, scale);
		state.submit(stack, collector, s.cahaya, OverlayTexture.NO_OVERLAY, 0);
		stack.popPose();
		label(stack, collector, camera, Component.literal(s.prop + "  ").append(Component.translatable(PropId.kunciLang(s.prop))), dx, dy + 0.08F,
				dz, 0.12F);
	}

	/** A camera-facing label with a dark backing, full bright. */
	private static void label(PoseStack stack, SubmitNodeCollector collector, CameraRenderState camera, Component text, float x, float y, float z,
			float size) {
		Font font = Minecraft.getInstance().font;
		FormattedCharSequence seq = text.getVisualOrderText();
		float width = font.width(seq);
		float k = size / 9.0F;
		stack.pushPose();
		stack.translate(x, y, z);
		stack.rotate(camera.orientation);
		stack.scale(k, -k, k);
		collector.submitTextBackground(stack, -width / 2.0F - 2.0F, -5.5F, width / 2.0F + 2.0F, 5.0F, 0x99000000, Font.DisplayMode.NORMAL,
				LightCoordsUtil.FULL_BRIGHT);
		collector.submitText(stack, -width / 2.0F, -4.5F, seq, false, Font.DisplayMode.NORMAL, LightCoordsUtil.FULL_BRIGHT, 0xFFF4E4C0, 0, 0);
		stack.popPose();
	}

	private PamerModel() {
	}
}
