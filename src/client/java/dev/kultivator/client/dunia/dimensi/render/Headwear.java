package dev.kultivator.client.dunia.dimensi.render;

import dev.kultivator.Kultivator;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * The head pieces of module D's people (DESIGN 34.5.13-14), each a cuboid model on the player head (it turns with the
 * head through the PlayerModel animation): Meng Po's white bun with gold pins, the judges' winged black hat (wushamao),
 * King Yama's crown board with jade tassels (mianguan), Ox-Head's horned head, Horse-Face's long face and mane, the
 * yaksha's horns, tusks and flame hair, the Dragon King's antlers and gold crown, the hermit's straw hat, the Penglai
 * immortal's jade crown and long white hair, the palace guard's helmet, the hungry ghost's hair and burning eyes, the
 * merchant's cap.
 * <p>
 * One texture serves them all ({@link #TEXTURE}, 64 × 512, made by {@code tools/gen_dunia_dimensi_textures.py}): 16
 * material bands of 32 px ({@code BAND_*}), every cube's UV unwrap fits inside its band (w + d ≤ 32, d + h ≤ 32).
 */
public final class Headwear {

	public static final Identifier TEXTURE = Kultivator.id("textures/dunia/dimensi/entity/headwear.png");

	public static final int NONE = 0;
	public static final int MENG_PO = 1;
	public static final int JUDGE = 2;
	public static final int YAMA = 3;
	public static final int OX = 4;
	public static final int HORSE = 5;
	public static final int YAKSHA = 6;
	public static final int DRAGON_KING = 7;
	public static final int HERMIT = 8;
	public static final int PENGLAI = 9;
	public static final int GUARD = 10;
	public static final int GHOST_HAIR = 11;
	public static final int MERCHANT = 12;
	public static final int GHOST_EYES = 13;
	public static final int KINDS = 14;

	public static final int BAND_BLACK = 0;
	public static final int BAND_GOLD = 1;
	public static final int BAND_HORN = 2;
	public static final int BAND_FUR = 3;
	public static final int BAND_WHITE = 4;
	public static final int BAND_STRAW = 5;
	public static final int BAND_JADE = 6;
	public static final int BAND_CRIMSON = 7;
	public static final int BAND_FUR_DARK = 8;
	public static final int BAND_GLOW = 9;
	public static final int BAND_TEAL = 10;
	public static final int BAND_PEARL = 11;

	private static final ModelLayerLocation[] LAYERS = new ModelLayerLocation[KINDS];

	static {
		for (int i = 1; i < KINDS; i++) {
			LAYERS[i] = new ModelLayerLocation(Kultivator.id("dimensi_head_" + i), "main");
		}
	}

	public static ModelLayerLocation layer(int kind) {
		return LAYERS[Mth.clamp(kind, 1, KINDS - 1)];
	}

	/** Registers every head piece's model layer (client init, before the entity models are baked). */
	public static void register() {
		for (int i = 1; i < KINDS; i++) {
			int kind = i;
			ModelLayerRegistry.registerModelLayer(LAYERS[i], () -> mesh(kind));
		}
	}

	private static CubeListBuilder band(int band) {
		return CubeListBuilder.create().texOffs(0, band * 32);
	}

	private static float rad(float degrees) {
		return degrees * Mth.DEG_TO_RAD;
	}

	/** The player mesh with only the head replaced by the piece's cubes (the other parts are never drawn). */
	static LayerDefinition mesh(int kind) {
		MeshDefinition mesh = PlayerModel.createMesh(CubeDeformation.NONE, false);
		PartDefinition root = mesh.getRoot();
		PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);
		head.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
		switch (kind) {
			case MENG_PO -> {
				head.addOrReplaceChild("cap", band(BAND_WHITE).addBox(-4.3F, -8.6F, -4.3F, 8.6F, 2.4F, 8.6F), PartPose.ZERO);
				head.addOrReplaceChild("back", band(BAND_WHITE).addBox(-4.3F, -8.0F, 3.1F, 8.6F, 6.5F, 1.2F), PartPose.ZERO);
				head.addOrReplaceChild("bun", band(BAND_WHITE).addBox(-2.5F, -3.0F, -2.5F, 5.0F, 3.0F, 5.0F), PartPose.offset(0.0F, -8.4F, 1.5F));
				head.addOrReplaceChild("pin_a", band(BAND_GOLD).addBox(-4.0F, -0.4F, -0.4F, 8.0F, 0.8F, 0.8F),
						PartPose.offsetAndRotation(0.0F, -10.0F, 1.5F, 0.0F, rad(25.0F), rad(-8.0F)));
				head.addOrReplaceChild("pin_b", band(BAND_JADE).addBox(-3.5F, -0.4F, -0.4F, 7.0F, 0.8F, 0.8F),
						PartPose.offsetAndRotation(0.0F, -10.6F, 1.5F, 0.0F, rad(-30.0F), rad(6.0F)));
				head.addOrReplaceChild("band", band(BAND_CRIMSON).addBox(-4.4F, -6.6F, -4.4F, 8.8F, 0.8F, 8.8F), PartPose.ZERO);
			}
			case JUDGE -> {
				head.addOrReplaceChild("crown", band(BAND_BLACK).addBox(-4.5F, -11.0F, -4.5F, 9.0F, 3.0F, 9.0F)
						.addBox(-3.5F, -12.5F, -2.0F, 7.0F, 1.5F, 6.0F), PartPose.ZERO);
				head.addOrReplaceChild("rim", band(BAND_GOLD).addBox(-4.7F, -8.4F, -4.7F, 9.4F, 0.9F, 9.4F), PartPose.ZERO);
				head.addOrReplaceChild("gem", band(BAND_JADE).addBox(-1.0F, -10.6F, -4.9F, 2.0F, 2.0F, 0.5F), PartPose.ZERO);
				head.addOrReplaceChild("wing_l", band(BAND_BLACK).addBox(0.0F, -0.5F, -1.0F, 8.0F, 1.0F, 2.5F),
						PartPose.offsetAndRotation(4.4F, -11.0F, 2.6F, 0.0F, 0.0F, rad(-6.0F)));
				head.addOrReplaceChild("wing_r", band(BAND_BLACK).addBox(-8.0F, -0.5F, -1.0F, 8.0F, 1.0F, 2.5F),
						PartPose.offsetAndRotation(-4.4F, -11.0F, 2.6F, 0.0F, 0.0F, rad(6.0F)));
			}
			case YAMA -> {
				head.addOrReplaceChild("cap", band(BAND_BLACK).addBox(-3.5F, -10.5F, -3.5F, 7.0F, 2.6F, 7.0F), PartPose.ZERO);
				head.addOrReplaceChild("board", band(BAND_BLACK).addBox(-6.0F, -11.5F, -7.5F, 12.0F, 1.0F, 15.0F), PartPose.ZERO);
				head.addOrReplaceChild("board_rim", band(BAND_GOLD).addBox(-6.2F, -11.0F, -7.7F, 12.4F, 0.5F, 0.4F)
						.addBox(-6.2F, -11.0F, 7.3F, 12.4F, 0.5F, 0.4F), PartPose.ZERO);
				head.addOrReplaceChild("pin", band(BAND_GOLD).addBox(-7.0F, -9.8F, -0.6F, 14.0F, 1.2F, 1.2F), PartPose.ZERO);
				CubeListBuilder tassels = band(BAND_JADE);
				for (int i = 0; i < 5; i++) {
					float x = -4.8F + i * 2.4F;
					tassels.addBox(x - 0.35F, -10.5F, -7.4F, 0.7F, 4.5F, 0.7F);
					tassels.addBox(x - 0.35F, -10.5F, 6.7F, 0.7F, 4.5F, 0.7F);
				}
				head.addOrReplaceChild("tassels", tassels, PartPose.ZERO);
				head.addOrReplaceChild("beard", band(BAND_BLACK).addBox(-2.5F, -1.0F, -4.7F, 5.0F, 5.0F, 1.0F), PartPose.ZERO);
			}
			case OX -> {
				head.addOrReplaceChild("skull", band(BAND_FUR).addBox(-5.0F, -10.0F, -5.5F, 10.0F, 10.0F, 11.0F), PartPose.ZERO);
				head.addOrReplaceChild("snout", band(BAND_FUR).addBox(-3.5F, -5.0F, -9.0F, 7.0F, 5.0F, 4.0F), PartPose.ZERO);
				head.addOrReplaceChild("nose", band(BAND_FUR_DARK).addBox(-3.0F, -4.6F, -9.3F, 6.0F, 2.5F, 0.5F), PartPose.ZERO);
				head.addOrReplaceChild("ring", band(BAND_GOLD).addBox(-1.5F, -2.4F, -9.7F, 3.0F, 2.0F, 0.6F), PartPose.ZERO);
				head.addOrReplaceChild("eyes", band(BAND_GLOW).addBox(-4.0F, -7.0F, -5.7F, 2.0F, 1.0F, 0.4F)
						.addBox(2.0F, -7.0F, -5.7F, 2.0F, 1.0F, 0.4F), PartPose.ZERO);
				for (int side = -1; side <= 1; side += 2) {
					String s = side < 0 ? "r" : "l";
					PartDefinition base = head.addOrReplaceChild("horn_" + s, band(BAND_HORN)
									.addBox(side < 0 ? -5.0F : 0.0F, -1.2F, -1.2F, 5.0F, 2.4F, 2.4F),
							PartPose.offsetAndRotation(side * 4.5F, -9.0F, -1.5F, 0.0F, 0.0F, rad(side * -18.0F)));
					base.addOrReplaceChild("tip", band(BAND_HORN).addBox(side < 0 ? -4.0F : 0.0F, -0.8F, -0.8F, 4.0F, 1.6F, 1.6F),
							PartPose.offsetAndRotation(side * 4.6F, 0.0F, 0.0F, 0.0F, 0.0F, rad(side * -50.0F)));
					head.addOrReplaceChild("ear_" + s, band(BAND_FUR_DARK).addBox(side < 0 ? -3.0F : 0.0F, -0.8F, -1.5F, 3.0F, 1.6F, 3.0F),
							PartPose.offsetAndRotation(side * 5.0F, -6.5F, 0.5F, 0.0F, 0.0F, rad(side * 20.0F)));
				}
			}
			case HORSE -> {
				head.addOrReplaceChild("skull", band(BAND_FUR_DARK).addBox(-4.6F, -10.0F, -4.6F, 9.2F, 10.0F, 9.2F), PartPose.ZERO);
				head.addOrReplaceChild("face", band(BAND_FUR_DARK).addBox(-3.0F, -9.0F, -11.5F, 6.0F, 6.0F, 7.0F)
						.addBox(-2.6F, -3.2F, -11.0F, 5.2F, 2.6F, 6.0F), PartPose.ZERO);
				head.addOrReplaceChild("nostrils", band(BAND_BLACK).addBox(-2.0F, -7.5F, -11.8F, 4.0F, 1.5F, 0.4F), PartPose.ZERO);
				head.addOrReplaceChild("eyes", band(BAND_GLOW).addBox(-3.2F, -8.0F, -6.0F, 0.4F, 1.0F, 2.0F)
						.addBox(2.8F, -8.0F, -6.0F, 0.4F, 1.0F, 2.0F), PartPose.ZERO);
				head.addOrReplaceChild("ears", band(BAND_FUR_DARK).addBox(-3.2F, -13.5F, -1.0F, 1.6F, 3.5F, 1.6F)
						.addBox(1.6F, -13.5F, -1.0F, 1.6F, 3.5F, 1.6F), PartPose.ZERO);
				head.addOrReplaceChild("mane", band(BAND_BLACK).addBox(-1.0F, -12.0F, -2.0F, 2.0F, 4.0F, 7.0F)
						.addBox(-1.0F, -9.0F, 4.0F, 2.0F, 10.0F, 2.0F), PartPose.ZERO);
			}
			case YAKSHA -> {
				for (int side = -1; side <= 1; side += 2) {
					String s = side < 0 ? "r" : "l";
					PartDefinition horn = head.addOrReplaceChild("horn_" + s, band(BAND_HORN).addBox(-0.8F, -4.5F, -0.8F, 1.6F, 4.5F, 1.6F),
							PartPose.offsetAndRotation(side * 2.6F, -8.0F, -1.5F, rad(-18.0F), 0.0F, rad(side * 16.0F)));
					horn.addOrReplaceChild("tip", band(BAND_HORN).addBox(-0.5F, -3.0F, -0.5F, 1.0F, 3.0F, 1.0F),
							PartPose.offsetAndRotation(0.0F, -4.4F, 0.0F, rad(-25.0F), 0.0F, 0.0F));
					head.addOrReplaceChild("tusk_" + s, band(BAND_PEARL).addBox(-0.5F, -2.0F, -0.4F, 1.0F, 2.4F, 0.8F),
							PartPose.offsetAndRotation(side * 2.2F, -0.8F, -4.3F, 0.0F, 0.0F, rad(side * 10.0F)));
				}
				head.addOrReplaceChild("flames", band(BAND_CRIMSON).addBox(-3.0F, -10.0F, -2.0F, 2.0F, 2.5F, 6.0F)
						.addBox(-0.8F, -11.0F, -1.0F, 1.6F, 3.5F, 6.0F).addBox(1.0F, -10.0F, -2.0F, 2.0F, 2.5F, 6.0F), PartPose.ZERO);
			}
			case DRAGON_KING -> {
				head.addOrReplaceChild("crown", band(BAND_GOLD).addBox(-3.0F, -10.2F, -3.0F, 6.0F, 2.2F, 6.0F)
						.addBox(-2.0F, -12.6F, -3.6F, 4.0F, 3.0F, 0.6F), PartPose.ZERO);
				head.addOrReplaceChild("pearl", band(BAND_PEARL).addBox(-0.8F, -11.8F, -4.2F, 1.6F, 1.6F, 0.6F), PartPose.ZERO);
				for (int side = -1; side <= 1; side += 2) {
					String s = side < 0 ? "r" : "l";
					PartDefinition antler = head.addOrReplaceChild("antler_" + s, band(BAND_HORN)
									.addBox(-0.6F, -5.0F, -0.6F, 1.2F, 5.0F, 1.2F),
							PartPose.offsetAndRotation(side * 3.0F, -8.6F, 0.0F, rad(-15.0F), 0.0F, rad(side * 28.0F)));
					antler.addOrReplaceChild("prong", band(BAND_HORN).addBox(-0.5F, -2.5F, -0.5F, 1.0F, 2.5F, 1.0F),
							PartPose.offsetAndRotation(0.0F, -2.5F, 0.0F, rad(-40.0F), 0.0F, rad(side * -35.0F)));
					antler.addOrReplaceChild("top", band(BAND_HORN).addBox(-0.5F, -3.0F, -0.5F, 1.0F, 3.0F, 1.0F),
							PartPose.offsetAndRotation(0.0F, -5.0F, 0.0F, rad(20.0F), 0.0F, rad(side * -15.0F)));
					head.addOrReplaceChild("whisker_" + s, band(BAND_GOLD).addBox(side < 0 ? -5.0F : 0.0F, -0.3F, -0.3F, 5.0F, 0.6F, 0.6F),
							PartPose.offsetAndRotation(side * 1.5F, -2.0F, -4.4F, 0.0F, rad(side * 20.0F), rad(side * 25.0F)));
				}
				head.addOrReplaceChild("beard", band(BAND_WHITE).addBox(-2.0F, -0.5F, -4.6F, 4.0F, 5.5F, 1.0F), PartPose.ZERO);
			}
			case HERMIT -> {
				head.addOrReplaceChild("straw", band(BAND_STRAW).addBox(-8.0F, -9.0F, -8.0F, 16.0F, 1.0F, 16.0F)
						.addBox(-6.0F, -10.0F, -6.0F, 12.0F, 1.0F, 12.0F).addBox(-4.0F, -11.0F, -4.0F, 8.0F, 1.0F, 8.0F)
						.addBox(-2.0F, -12.0F, -2.0F, 4.0F, 1.0F, 4.0F).addBox(-0.5F, -13.0F, -0.5F, 1.0F, 1.0F, 1.0F), PartPose.ZERO);
				head.addOrReplaceChild("beard", band(BAND_WHITE).addBox(-2.0F, -1.0F, -4.6F, 4.0F, 4.5F, 1.0F), PartPose.ZERO);
			}
			case PENGLAI -> {
				head.addOrReplaceChild("guan", band(BAND_JADE).addBox(-1.5F, -11.0F, -1.5F, 3.0F, 3.0F, 3.0F), PartPose.ZERO);
				head.addOrReplaceChild("pin", band(BAND_GOLD).addBox(-3.8F, -10.0F, -0.4F, 7.6F, 0.8F, 0.8F), PartPose.ZERO);
				head.addOrReplaceChild("hair", band(BAND_WHITE).addBox(-4.3F, -8.6F, -4.3F, 8.6F, 1.6F, 8.6F)
						.addBox(-4.3F, -8.0F, 3.2F, 8.6F, 10.0F, 1.2F), PartPose.ZERO);
				head.addOrReplaceChild("beard", band(BAND_WHITE).addBox(-2.0F, -1.0F, -4.6F, 4.0F, 7.0F, 1.0F), PartPose.ZERO);
			}
			case GUARD -> {
				head.addOrReplaceChild("helm", band(BAND_TEAL).addBox(-4.6F, -9.0F, -4.6F, 9.2F, 4.4F, 9.2F)
						.addBox(-4.7F, -4.8F, -3.0F, 0.6F, 4.0F, 5.0F).addBox(4.1F, -4.8F, -3.0F, 0.6F, 4.0F, 5.0F), PartPose.ZERO);
				head.addOrReplaceChild("crest", band(BAND_GOLD).addBox(-0.6F, -10.0F, -4.8F, 1.2F, 2.0F, 9.6F), PartPose.ZERO);
				head.addOrReplaceChild("plume", band(BAND_CRIMSON).addBox(-0.5F, -14.0F, -1.0F, 1.0F, 4.5F, 4.0F), PartPose.ZERO);
			}
			case GHOST_HAIR -> {
				head.addOrReplaceChild("hair", band(BAND_BLACK).addBox(-4.4F, -8.7F, -4.4F, 8.8F, 1.8F, 8.8F)
						.addBox(-4.4F, -8.0F, 3.4F, 8.8F, 12.0F, 1.0F).addBox(-4.6F, -8.0F, -4.6F, 1.4F, 11.0F, 6.0F)
						.addBox(3.2F, -8.0F, -4.6F, 1.4F, 11.0F, 6.0F), PartPose.ZERO);
			}
			case MERCHANT -> {
				head.addOrReplaceChild("cap", band(BAND_TEAL).addBox(-4.4F, -9.6F, -4.4F, 8.8F, 2.4F, 8.8F), PartPose.ZERO);
				head.addOrReplaceChild("knob", band(BAND_GOLD).addBox(-0.8F, -10.8F, -0.8F, 1.6F, 1.2F, 1.6F), PartPose.ZERO);
			}
			case GHOST_EYES -> head.addOrReplaceChild("eyes", band(BAND_GLOW).addBox(-3.2F, -5.2F, -4.35F, 2.0F, 1.0F, 0.3F)
					.addBox(1.2F, -5.2F, -4.35F, 2.0F, 1.0F, 0.3F), PartPose.ZERO);
			default -> {
			}
		}
		return LayerDefinition.create(mesh, 64, 512);
	}

	private Headwear() {
	}
}
