package dev.kultivator.client.dunia.tempat.tema.model;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.tempat.tema.gunung.KeraGunungEntity;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * Kera gunung ({@code kultivator:kera_gunung}, DESIGN 38.T.9.2): the mountain macaque of the Kota Gunung top row, built
 * at the design's sizes and drawn at 0.62 so it stands 0.7 blocks (the Kera Putih Tua is a further 1.8 x through its
 * {@code SCALE} attribute). A body pivoting at the hip (8 x 10 x 12), a head with the pink bare face, muzzle, brow
 * ridge, ears and dark eyes, long arms, short hind legs and a curled tail; the elder adds a white beard and long brow
 * tufts. When it has snatched something it carries a cloth-wrapped bundle in its right hand.
 * <p>
 * States: walking/running on four legs (gait from the walk animation); {@link KeraGunungEntity#KEADAAN_DUDUK} sits up on
 * its haunches and scratches its head; {@link KeraGunungEntity#KEADAAN_REBUT} swipes the right arm in 6 ticks and the
 * bundle appears; {@link KeraGunungEntity#KEADAAN_KABUR} gallops with the bundle; {@link
 * KeraGunungEntity#KEADAAN_PANJAT} stretches into a climbing leap; the elder's {@link KeraGunungEntity#KEADAAN_TUNJUK}
 * sits and points and {@link KeraGunungEntity#KEADAAN_MEMBUNGKUK} bows with its hands together. Textures
 * {@code kera_gunung.png}, {@code kera_gunung_putih.png} (64 x 224, bands of 32).
 */
public class KeraGunungRenderer extends TemaMakhlukRenderer<KeraGunungEntity, KeraGunungRenderer.State, KeraGunungRenderer.Model> {

	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Kultivator.id("kera_gunung"), "main");
	private static final Identifier TEX = Kultivator.id("textures/entity/tempat/tema/kera_gunung.png");
	private static final Identifier TEX_PUTIH = Kultivator.id("textures/entity/tempat/tema/kera_gunung_putih.png");

	static final int B = 32;
	static final int BULU = 0;
	static final int BULU_MUDA = 1;
	static final int MUKA = 2;
	static final int MATA = 3;
	static final int EKOR = 4;
	static final int BUNTALAN = 5;
	static final int JANGGUT = 6;

	public static class State extends TemaMakhlukRenderer.State {
		public boolean tua;
		public boolean bawa;
		public float rebut;
	}

	public KeraGunungRenderer(EntityRendererProvider.Context context) {
		super(context, new Model(context.bakeLayer(LAYER)), 0.3F, 0.62F, new Identifier[] {TEX, TEX_PUTIH}, null);
	}

	public static void register() {
		ModelLayerRegistry.registerModelLayer(LAYER, KeraGunungRenderer::mesh);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	protected double batas() {
		return 80.0D;
	}

	@Override
	protected double jauhKecil() {
		return 40.0D;
	}

	@Override
	public void extractRenderState(KeraGunungEntity entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.tua = state.varian == KeraGunungEntity.VARIAN_PUTIH_TUA;
		state.rebut = state.keadaan == KeraGunungEntity.KEADAAN_REBUT ? maju(state.umur, 0.0F, 6.0F) : 0.0F;
		state.bawa = state.keadaan == KeraGunungEntity.KEADAAN_KABUR || (state.keadaan == KeraGunungEntity.KEADAAN_REBUT && state.rebut > 0.5F);
	}

	private static CubeListBuilder p(int band) {
		return CubeListBuilder.create().texOffs(0, band * B);
	}

	private static CubeListBuilder p(int band, int u) {
		return CubeListBuilder.create().texOffs(u, band * B);
	}

	static LayerDefinition mesh() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		// body from the hip forward, slightly nose-up at rest
		PartDefinition badan = root.addOrReplaceChild("badan", p(BULU).addBox(-4.0F, -8.0F, -12.0F, 8.0F, 10.0F, 12.0F)
				.addBox(-3.5F, -8.6F, -10.0F, 7.0F, 1.0F, 8.0F), PartPose.offsetAndRotation(0.0F, 16.0F, 4.0F, -0.18F, 0.0F, 0.0F));
		badan.addOrReplaceChild("dada", p(BULU_MUDA).addBox(-3.0F, -4.0F, -12.6F, 6.0F, 6.0F, 1.0F), PartPose.ZERO);
		// head
		PartDefinition kepala = badan.addOrReplaceChild("kepala", p(BULU_MUDA, 10).addBox(-3.5F, -6.0F, -5.0F, 7.0F, 7.0F, 6.0F),
				PartPose.offset(0.0F, -5.0F, -12.0F));
		kepala.addOrReplaceChild("muka", p(MUKA).addBox(-2.5F, -5.0F, -5.6F, 5.0F, 4.0F, 1.0F).addBox(-1.8F, -2.0F, -6.8F, 3.6F, 2.4F, 2.0F),
				PartPose.ZERO);
		kepala.addOrReplaceChild("alis", p(BULU, 30).addBox(-3.0F, -5.8F, -5.9F, 6.0F, 1.0F, 1.0F), PartPose.ZERO);
		kepala.addOrReplaceChild("mata", p(MATA).addBox(-2.0F, -4.2F, -5.8F, 1.2F, 1.0F, 0.4F).addBox(0.8F, -4.2F, -5.8F, 1.2F, 1.0F, 0.4F), PartPose.ZERO);
		kepala.addOrReplaceChild("telinga", p(MUKA, 20).addBox(-4.4F, -4.5F, -2.0F, 1.0F, 2.0F, 2.0F).addBox(3.4F, -4.5F, -2.0F, 1.0F, 2.0F, 2.0F),
				PartPose.ZERO);
		kepala.addOrReplaceChild("janggut", p(JANGGUT).addBox(-2.0F, 0.0F, -6.2F, 4.0F, 4.0F, 1.0F).addBox(-2.6F, -1.0F, -5.2F, 5.2F, 2.0F, 2.0F), PartPose.ZERO);
		kepala.addOrReplaceChild("alis_tua", p(JANGGUT, 20).addBox(-4.0F, -6.2F, -6.2F, 3.0F, 1.0F, 1.2F).addBox(1.0F, -6.2F, -6.2F, 3.0F, 1.0F, 1.2F),
				PartPose.ZERO);
		// long arms from the shoulders
		for (int side = -1; side <= 1; side += 2) {
			PartDefinition lengan = badan.addOrReplaceChild(side < 0 ? "lengan_kiri" : "lengan_kanan", p(BULU, 40 + (side + 1) * 5)
					.addBox(-1.5F, 0.0F, -1.5F, 3.0F, 10.0F, 3.0F), PartPose.offset(side * 3.0F, -2.0F, -10.0F));
			lengan.addOrReplaceChild("tangan", p(MUKA, 30).addBox(-1.4F, 9.0F, -1.6F, 2.8F, 1.2F, 2.4F), PartPose.ZERO);
			if (side > 0) {
				PartDefinition buntal = lengan.addOrReplaceChild("buntalan", p(BUNTALAN).addBox(-2.0F, 9.0F, -4.5F, 4.0F, 3.5F, 4.0F)
						.addBox(-0.8F, 8.2F, -3.3F, 1.6F, 1.0F, 1.6F), PartPose.ZERO);
				buntal.addOrReplaceChild("simpul", p(BUNTALAN, 30).addBox(-1.0F, 7.4F, -3.5F, 2.0F, 1.0F, 2.0F), PartPose.ZERO);
			}
		}
		// curled tail: two segments from the rump
		PartDefinition ekor = badan.addOrReplaceChild("ekor", p(EKOR).addBox(-1.0F, -1.0F, 0.0F, 2.0F, 2.0F, 6.0F), PartPose.offsetAndRotation(0.0F, -6.0F,
				0.0F, 0.7F, 0.0F, 0.0F));
		ekor.addOrReplaceChild("ujung", p(EKOR, 20).addBox(-0.8F, -0.8F, 0.0F, 1.6F, 1.6F, 5.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 5.5F, 0.9F, 0.0F,
				0.0F));
		// hind legs from the hip
		for (int side = -1; side <= 1; side += 2) {
			PartDefinition kaki = root.addOrReplaceChild(side < 0 ? "kaki_kiri" : "kaki_kanan", p(BULU, 80 + (side + 1) * 5)
					.addBox(-1.5F, 0.0F, -1.5F, 3.0F, 8.0F, 3.0F), PartPose.offset(side * 2.5F, 16.0F, 3.5F));
			kaki.addOrReplaceChild("telapak", p(MUKA, 40).addBox(-1.5F, 7.0F, -3.0F, 3.0F, 1.0F, 3.0F), PartPose.ZERO);
		}
		return LayerDefinition.create(mesh, 64, B * 7);
	}

	/** The macaque's model: gait and the seven poses. */
	public static final class Model extends EntityModel<State> {

		private final ModelPart badan;
		private final ModelPart kepala;
		private final ModelPart janggut;
		private final ModelPart alisTua;
		private final ModelPart lenganKiri;
		private final ModelPart lenganKanan;
		private final ModelPart buntalan;
		private final ModelPart ekor;
		private final ModelPart ujung;
		private final ModelPart kakiKiri;
		private final ModelPart kakiKanan;

		public Model(ModelPart root) {
			super(root);
			this.badan = root.getChild("badan");
			this.kepala = badan.getChild("kepala");
			this.janggut = kepala.getChild("janggut");
			this.alisTua = kepala.getChild("alis_tua");
			this.lenganKiri = badan.getChild("lengan_kiri");
			this.lenganKanan = badan.getChild("lengan_kanan");
			this.buntalan = lenganKanan.getChild("buntalan");
			this.ekor = badan.getChild("ekor");
			this.ujung = ekor.getChild("ujung");
			this.kakiKiri = root.getChild("kaki_kiri");
			this.kakiKanan = root.getChild("kaki_kanan");
		}

		@Override
		public void setupAnim(State state) {
			super.setupAnim(state);
			float age = state.ageInTicks;
			boolean still = state.jauh;
			janggut.visible = state.tua;
			alisTua.visible = state.tua;
			buntalan.visible = state.bawa;
			float speed = Math.min(1.0F, state.walkAnimationSpeed * 2.2F);
			float pos = state.walkAnimationPos * (state.keadaan == KeraGunungEntity.KEADAAN_KABUR ? 1.4F : 1.1F);
			float lookYaw = Mth.clamp(state.yRot, -50.0F, 50.0F) * Mth.DEG_TO_RAD;
			kepala.yRot = lookYaw;
			kepala.xRot = 0.18F + state.xRot * Mth.DEG_TO_RAD * 0.5F;
			int k = state.keadaan;
			boolean duduk = k == KeraGunungEntity.KEADAAN_DUDUK || k == KeraGunungEntity.KEADAAN_TUNJUK || k == KeraGunungEntity.KEADAAN_MEMBUNGKUK;
			if (duduk) {
				// up on the haunches: body upright about the hip, hind legs folded forward, head level
				float body = k == KeraGunungEntity.KEADAAN_MEMBUNGKUK ? -0.55F : -1.25F;
				badan.xRot = body;
				badan.y += 4.0F;
				kakiKiri.y += 4.0F;
				kakiKanan.y += 4.0F;
				kakiKiri.xRot = -1.35F;
				kakiKanan.xRot = -1.35F;
				kepala.xRot = -body + 0.1F;
				float hang = -body;
				lenganKiri.xRot = hang - 0.2F;
				lenganKanan.xRot = hang - 0.2F;
				if (k == KeraGunungEntity.KEADAAN_DUDUK) {
					// scratching the head with the right hand
					lenganKanan.xRot = hang - 2.6F + (still ? 0.0F : Mth.sin(age * 1.1F) * 0.18F);
					lenganKanan.zRot = -0.35F;
					kepala.zRot = still ? 0.0F : Mth.sin(age * 0.55F) * 0.08F;
				} else if (k == KeraGunungEntity.KEADAAN_TUNJUK) {
					lenganKanan.xRot = hang - 1.75F;
					lenganKanan.yRot = -0.2F;
				} else {
					// bow: hands together in front, head lowered
					lenganKiri.xRot = hang - 1.1F;
					lenganKanan.xRot = hang - 1.1F;
					lenganKiri.zRot = -0.45F;
					lenganKanan.zRot = 0.45F;
					kepala.xRot = -body + 0.7F;
				}
				ekor.xRot = 1.5F;
				ujung.xRot = 0.4F;
				return;
			}
			if (k == KeraGunungEntity.KEADAAN_PANJAT) {
				// a stretched leap: reaching arms, hind legs kicked back
				badan.xRot = -0.55F;
				lenganKiri.xRot = -2.3F;
				lenganKanan.xRot = -2.3F;
				kakiKiri.xRot = 0.9F;
				kakiKanan.xRot = 0.9F;
				ekor.xRot = 0.2F;
				ujung.xRot = 0.1F;
				return;
			}
			// four-legged gait: diagonal pairs (a trot), galloping when fleeing
			float gallop = k == KeraGunungEntity.KEADAAN_KABUR ? 1.0F : 0.0F;
			float a = Mth.sin(pos) * 0.9F * speed;
			float b = Mth.sin(pos + Mth.PI * (1.0F - 0.5F * gallop)) * 0.9F * speed;
			lenganKiri.xRot = 0.18F + a;
			lenganKanan.xRot = 0.18F + b;
			kakiKiri.xRot = b;
			kakiKanan.xRot = a;
			badan.y += -Math.abs(Mth.sin(pos)) * 0.8F * speed;
			if (state.bawa) {
				// the loot is clutched to the chest while running on three legs
				lenganKanan.xRot = -0.9F;
				lenganKanan.zRot = 0.25F;
			}
			if (state.rebut > 0.0F) {
				float s = Mth.sin(state.rebut * Mth.PI);
				lenganKanan.xRot = 0.18F - 1.9F * s - (state.rebut > 0.5F ? 0.6F : 0.0F);
				badan.xRot -= 0.25F * s;
			}
			// the tail curls and sways
			ekor.xRot = 0.7F + (still ? 0.0F : Mth.sin(age * 0.11F) * 0.12F) - speed * 0.3F;
			ekor.yRot = still ? 0.0F : Mth.sin(age * 0.07F + state.fase) * 0.25F;
			ujung.xRot = 0.9F - speed * 0.4F;
		}
	}
}
