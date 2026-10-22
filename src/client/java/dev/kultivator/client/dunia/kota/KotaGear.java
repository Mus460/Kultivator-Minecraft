package dev.kultivator.client.dunia.kota;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

/**
 * The gear of module K's people (DESIGN 34.5.1-34.5.4, detail pass): cuboid models on the player skeleton (head, body,
 * arms), so every piece nods, walks, bows and duels with the body through the PlayerModel animation, the meditation seat
 * and the cast/duel poses. One outfit per role (four for the townsfolk, two for the wanderers, a leader's for the rival
 * groups):
 * <ul>
 * <li>Juru Lelang: the black futou with its stiff side feet, a sash, a jade pendant and a folded fan at the waist (gold
 * futou for the Balai Lelang Abadi).</li>
 * <li>Pemilik Teh: a hair bun with a wooden pin, an apron, a towel over the shoulder.</li>
 * <li>Makelar Gelap: a hood with a face veil, a gold earring, a mantle and a dagger at the belt.</li>
 * <li>Pedagang Pil: a merchant cap with a gold knob, a pill gourd and a purse (gold cap and abacus in the Pasar Abadi).</li>
 * <li>Pandai Pusaka: a crimson headband, a leather apron and bracers.</li>
 * <li>Penjaga Kota: a steel helmet with gold crest and crimson plume, cheek and neck guards, a lamellar cuirass with its
 * heart-protecting mirror, a tabard in the town's colour and pauldrons.</li>
 * <li>Warga: a straw sun hat, a headscarf, a bun with a jade pin, or a topknot with a white cloth; a sash.</li>
 * <li>Pengembara: the bamboo douli with gauze veils, a cloak, a sword in its sheath at the left hip and a wine gourd; or
 * a topknot under a jade guan with ribbons and a sword across the back.</li>
 * <li>Saingan: the group's headband with flying tails, a hip sword, a leather shoulder guard; the leader wears a gold guan
 * and a cloak.</li>
 * <li>Abadi: a jade guan with a long gold pin, long hair and the floating pibo shawl.</li>
 * <li>Penceramah: a tall scholar's hat, a long white beard and brows, a horsetail whisk.</li>
 * <li>Utusan: the winged official's hat (wushamao) with gold wings and a jade belt.</li>
 * <li>Penjaga Kebun: a crown of peach blossoms and a basket of peaches.</li>
 * </ul>
 * Each outfit has a fixed-colour mesh and a cloth mesh tinted per NPC ({@link #tint}). The sheathed sword's hilt is a part
 * named {@link #HILT} that the layer hides while the blade is drawn. One texture serves all ({@link #TEXTURE}, 64 × 512,
 * {@code tools/gen_dunia_kota_gear.py}): sixteen material bands of 32 px; every cube's unwrap fits inside its band.
 */
public final class KotaGear {

	public static final Identifier TEXTURE = Kultivator.id("textures/dunia/kota/entity/perlengkapan.png");
	/** The sheathed sword's hilt (hidden while the blade is in hand). */
	public static final String HILT = "hilt";

	public static final int NONE = 0;
	public static final int FUTOU = 1;
	public static final int FUTOU_GOLD = 2;
	public static final int TEA = 3;
	public static final int BROKER = 4;
	public static final int MERCHANT = 5;
	public static final int MERCHANT_GOLD = 6;
	public static final int SMITH = 7;
	public static final int GUARD = 8;
	public static final int FOLK_HAT = 9;
	public static final int FOLK_SCARF = 10;
	public static final int FOLK_BUN = 11;
	public static final int FOLK_KNOT = 12;
	public static final int WANDERER_DOULI = 13;
	public static final int WANDERER_GUAN = 14;
	public static final int RIVAL = 15;
	public static final int RIVAL_LEADER = 16;
	public static final int IMMORTAL = 17;
	public static final int SAGE = 18;
	public static final int ENVOY = 19;
	public static final int GARDEN = 20;
	public static final int KINDS = 21;

	public static final int BAND_BLACK = 0;
	public static final int BAND_GOLD = 1;
	public static final int BAND_JADE = 2;
	public static final int BAND_CRIMSON = 3;
	public static final int BAND_WHITE = 4;
	public static final int BAND_STRAW = 5;
	public static final int BAND_LEATHER = 6;
	public static final int BAND_STEEL = 7;
	public static final int BAND_WOOD = 8;
	public static final int BAND_CLOTH = 9;
	public static final int BAND_PEACH = 10;
	public static final int BAND_GOURD = 11;
	public static final int BAND_BRONZE = 12;
	public static final int BAND_PAPER = 13;
	public static final int BAND_HAIR = 14;
	public static final int BAND_GAUZE = 15;

	private static final ModelLayerLocation[] SOLID = new ModelLayerLocation[KINDS];
	private static final ModelLayerLocation[] CLOTH = new ModelLayerLocation[KINDS];

	static {
		for (int i = 1; i < KINDS; i++) {
			SOLID[i] = new ModelLayerLocation(Kultivator.id("kota_gear_" + i), "main");
			CLOTH[i] = new ModelLayerLocation(Kultivator.id("kota_gear_" + i), "cloth");
		}
	}

	public static ModelLayerLocation solid(int kind) {
		return SOLID[Mth.clamp(kind, 1, KINDS - 1)];
	}

	public static ModelLayerLocation cloth(int kind) {
		return CLOTH[Mth.clamp(kind, 1, KINDS - 1)];
	}

	/** Registers every outfit's two layers (client init, before the entity models are baked). */
	public static void register() {
		for (int i = 1; i < KINDS; i++) {
			int kind = i;
			ModelLayerRegistry.registerModelLayer(SOLID[i], () -> mesh(kind, false));
			ModelLayerRegistry.registerModelLayer(CLOTH[i], () -> mesh(kind, true));
		}
	}

	/** The outfit of an NPC (role, look seed, the leader flag). */
	public static int kindOf(KultivatorNpc npc) {
		int look = npc.look();
		return switch (npc.role()) {
			case JURU_LELANG -> FUTOU;
			case JURU_LELANG_ABADI -> FUTOU_GOLD;
			case PEMILIK_TEH -> TEA;
			case MAKELAR_GELAP -> BROKER;
			case PEDAGANG_PIL -> MERCHANT;
			case PEDAGANG_ABADI -> MERCHANT_GOLD;
			case PANDAI_PUSAKA -> SMITH;
			case PENJAGA -> GUARD;
			case WARGA -> FOLK_HAT + Math.floorMod(look >>> 9, 4);
			case PENGEMBARA -> Math.floorMod(look >>> 21, 2) == 0 ? WANDERER_DOULI : WANDERER_GUAN;
			case SAINGAN -> npc.hasFlag(KultivatorNpc.FLAG_LEADER) ? RIVAL_LEADER : RIVAL;
			case ABADI -> IMMORTAL;
			case PENCERAMAH -> SAGE;
			case UTUSAN -> ENVOY;
			case PENJAGA_KEBUN -> GARDEN;
		};
	}

	/** The cloth colour of an outfit for a robe colour: darker trims, a light apron, the rival's own colour. */
	public static int tint(int kind, int robe) {
		int r = ARGB.red(robe);
		int g = ARGB.green(robe);
		int b = ARGB.blue(robe);
		return switch (kind) {
			case TEA, FOLK_SCARF -> ARGB.color(255, (r + 3 * 236) / 4, (g + 3 * 232) / 4, (b + 3 * 220) / 4);
			case RIVAL, RIVAL_LEADER -> ARGB.color(255, Math.min(255, r + 30), Math.min(255, g + 30), Math.min(255, b + 30));
			case IMMORTAL, SAGE, ENVOY -> ARGB.color(255, 250, 238, 200);
			case GUARD -> ARGB.color(255, Math.max(40, r * 3 / 4), Math.max(40, g * 3 / 4), Math.max(60, b * 3 / 4));
			default -> ARGB.color(255, r * 3 / 4, g * 3 / 4, b * 3 / 4);
		};
	}

	private static float rad(float degrees) {
		return degrees * Mth.DEG_TO_RAD;
	}

	// ================================================================== meshes

	/** Builds one pass of an outfit: the fixed materials ({@code cloth} false) or the tinted cloth ({@code cloth} true). */
	private static final class Pass {
		final boolean cloth;

		Pass(boolean cloth) {
			this.cloth = cloth;
		}

		/** A part of fixed material {@code band} (empty in the cloth pass, so children keep their parents). */
		PartDefinition solid(PartDefinition parent, String name, int band, PartPose pose, float... boxes) {
			return parent.addOrReplaceChild(name, cloth ? CubeListBuilder.create() : cubes(band, boxes), pose);
		}

		/** A part of the tinted cloth (empty in the fixed pass). */
		PartDefinition tinted(PartDefinition parent, String name, PartPose pose, float... boxes) {
			return parent.addOrReplaceChild(name, cloth ? cubes(BAND_CLOTH, boxes) : CubeListBuilder.create(), pose);
		}

		private static CubeListBuilder cubes(int band, float[] boxes) {
			CubeListBuilder builder = CubeListBuilder.create().texOffs(0, band * 32);
			for (int i = 0; i + 5 < boxes.length; i += 6) {
				builder.addBox(boxes[i], boxes[i + 1], boxes[i + 2], boxes[i + 3], boxes[i + 4], boxes[i + 5]);
			}
			return builder;
		}
	}

	/** The player skeleton with every skin cube removed (the gear's parts hang on it). */
	private static MeshDefinition skeleton() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);
		head.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
		body.addOrReplaceChild("jacket", CubeListBuilder.create(), PartPose.ZERO);
		PartDefinition rightArm = root.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.offset(-5.0F, 2.0F, 0.0F));
		rightArm.addOrReplaceChild("right_sleeve", CubeListBuilder.create(), PartPose.ZERO);
		PartDefinition leftArm = root.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.offset(5.0F, 2.0F, 0.0F));
		leftArm.addOrReplaceChild("left_sleeve", CubeListBuilder.create(), PartPose.ZERO);
		PartDefinition rightLeg = root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.offset(-1.9F, 12.0F, 0.0F));
		rightLeg.addOrReplaceChild("right_pants", CubeListBuilder.create(), PartPose.ZERO);
		PartDefinition leftLeg = root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.offset(1.9F, 12.0F, 0.0F));
		leftLeg.addOrReplaceChild("left_pants", CubeListBuilder.create(), PartPose.ZERO);
		return mesh;
	}

	static LayerDefinition mesh(int kind, boolean cloth) {
		MeshDefinition mesh = skeleton();
		PartDefinition root = mesh.getRoot();
		PartDefinition head = root.getChild("head");
		PartDefinition body = root.getChild("body");
		PartDefinition rightArm = root.getChild("right_arm");
		PartDefinition leftArm = root.getChild("left_arm");
		Pass p = new Pass(cloth);
		PartPose zero = PartPose.ZERO;
		switch (kind) {
			case FUTOU, FUTOU_GOLD -> {
				int cap = kind == FUTOU_GOLD ? BAND_GOLD : BAND_BLACK;
				p.solid(head, "cap", cap, zero, -4.4F, -9.2F, -4.4F, 8.8F, 3.2F, 8.8F, -3.0F, -11.0F, 0.4F, 6.0F, 2.0F, 4.0F);
				p.solid(head, "band", kind == FUTOU_GOLD ? BAND_JADE : BAND_GOLD, zero, -4.55F, -6.6F, -4.55F, 9.1F, 0.6F, 9.1F);
				p.solid(head, "foot_l", cap, PartPose.offsetAndRotation(3.2F, -9.6F, 3.4F, 0.0F, 0.0F, rad(-10.0F)), 0.0F, -0.4F, -0.4F, 5.5F, 0.8F, 0.8F);
				p.solid(head, "foot_r", cap, PartPose.offsetAndRotation(-3.2F, -9.6F, 3.4F, 0.0F, 0.0F, rad(10.0F)), -5.5F, -0.4F, -0.4F, 5.5F, 0.8F, 0.8F);
				p.tinted(body, "sash", zero, -4.35F, 6.4F, -2.35F, 8.7F, 1.6F, 4.7F);
				p.solid(body, "pendant", BAND_JADE, zero, -0.7F, 8.1F, -2.7F, 1.4F, 2.2F, 0.4F);
				p.solid(body, "pendant_cord", BAND_CRIMSON, zero, -0.3F, 10.3F, -2.65F, 0.6F, 1.6F, 0.3F);
				PartDefinition fan = p.solid(body, "fan", BAND_PAPER, PartPose.offsetAndRotation(3.3F, 7.4F, -2.5F, 0.0F, 0.0F, rad(-14.0F)),
						-0.5F, 0.0F, -0.45F, 1.0F, 4.6F, 0.9F);
				p.solid(fan, "fan_rib", BAND_WOOD, zero, -0.55F, -0.6F, -0.5F, 1.1F, 0.6F, 1.0F);
				p.solid(fan, "fan_tassel", BAND_CRIMSON, zero, -0.25F, 4.6F, -0.25F, 0.5F, 1.8F, 0.5F);
			}
			case TEA -> {
				p.solid(head, "bun", BAND_HAIR, zero, -2.0F, -11.0F, -0.6F, 4.0F, 3.0F, 4.0F);
				p.solid(head, "pin", BAND_WOOD, PartPose.offsetAndRotation(0.0F, -10.0F, 1.4F, 0.0F, rad(22.0F), rad(-6.0F)),
						-3.6F, -0.3F, -0.3F, 7.2F, 0.6F, 0.6F);
				p.solid(head, "pin_bead", BAND_JADE, PartPose.offset(3.2F, -10.3F, 2.6F), -0.5F, -0.5F, -0.5F, 1.0F, 1.0F, 1.0F);
				p.tinted(head, "hairband", zero, -4.3F, -8.3F, -4.3F, 8.6F, 0.9F, 8.6F);
				p.tinted(body, "apron", zero, -3.6F, 5.4F, -2.55F, 7.2F, 7.8F, 0.5F);
				p.tinted(body, "apron_tie", zero, -4.35F, 5.5F, -2.35F, 8.7F, 1.1F, 4.7F, -1.0F, 6.6F, 2.3F, 0.8F, 3.0F, 0.3F);
				p.solid(body, "towel", BAND_WHITE, zero, 1.4F, -0.6F, -2.45F, 3.0F, 1.0F, 4.9F, 2.0F, 0.4F, -2.65F, 2.0F, 4.2F, 0.4F);
			}
			case BROKER -> {
				p.tinted(head, "hood", zero, -4.6F, -8.9F, -4.6F, 9.2F, 1.4F, 9.2F, -4.6F, -8.9F, 3.4F, 9.2F, 9.6F, 1.2F,
						-4.6F, -8.9F, -4.2F, 1.0F, 9.2F, 7.6F, 3.6F, -8.9F, -4.2F, 1.0F, 9.2F, 7.6F);
				p.solid(head, "veil", BAND_GAUZE, zero, -3.9F, -4.3F, -4.6F, 7.8F, 3.8F, 0.5F);
				p.solid(head, "earring", BAND_GOLD, zero, -4.8F, -3.2F, -1.0F, 0.4F, 1.4F, 0.4F);
				p.tinted(body, "mantle", zero, -4.6F, -0.3F, 2.2F, 9.2F, 12.5F, 0.7F, -4.6F, -0.4F, -2.5F, 9.2F, 2.2F, 4.9F);
				p.solid(body, "belt", BAND_LEATHER, zero, -4.3F, 7.0F, -2.3F, 8.6F, 1.2F, 4.6F);
				PartDefinition dagger = p.solid(body, "dagger", BAND_WOOD, PartPose.offsetAndRotation(-3.2F, 7.6F, -2.5F, 0.0F, 0.0F, rad(28.0F)),
						-0.45F, 0.0F, -0.45F, 0.9F, 4.0F, 0.9F);
				p.solid(dagger, "dagger_hilt", BAND_GOLD, zero, -0.3F, -1.6F, -0.3F, 0.6F, 1.6F, 0.6F, -0.8F, -0.3F, -0.5F, 1.6F, 0.4F, 1.0F);
			}
			case MERCHANT, MERCHANT_GOLD -> {
				if (kind == MERCHANT_GOLD) {
					p.solid(head, "cap", BAND_GOLD, zero, -4.4F, -9.4F, -4.4F, 8.8F, 2.4F, 8.8F);
					p.solid(head, "knob", BAND_JADE, zero, -0.8F, -10.6F, -0.8F, 1.6F, 1.2F, 1.6F);
				} else {
					p.tinted(head, "cap", zero, -4.4F, -9.4F, -4.4F, 8.8F, 2.4F, 8.8F);
					p.solid(head, "knob", BAND_GOLD, zero, -0.8F, -10.6F, -0.8F, 1.6F, 1.2F, 1.6F);
				}
				p.solid(head, "rim", BAND_BLACK, zero, -4.6F, -7.3F, -4.6F, 9.2F, 0.6F, 9.2F);
				p.solid(body, "belt", BAND_LEATHER, zero, -4.3F, 7.0F, -2.3F, 8.6F, 1.2F, 4.6F);
				p.solid(body, "purse", BAND_LEATHER, zero, 2.4F, 8.2F, -2.9F, 1.8F, 2.2F, 1.0F);
				if (kind == MERCHANT_GOLD) {
					PartDefinition abacus = p.solid(body, "abacus", BAND_BRONZE, PartPose.offset(-4.7F, 8.0F, 0.0F), -0.4F, 0.0F, -2.0F, 0.6F, 3.0F, 4.0F);
					p.solid(abacus, "beads", BAND_GOLD, zero, -0.6F, 0.6F, -1.6F, 1.0F, 0.6F, 3.2F, -0.6F, 1.8F, -1.6F, 1.0F, 0.6F, 3.2F);
					p.solid(body, "pendant", BAND_JADE, zero, -0.7F, 8.2F, -2.7F, 1.4F, 2.0F, 0.4F);
				} else {
					PartDefinition gourd = p.solid(body, "gourd", BAND_GOURD, PartPose.offset(-4.6F, 8.6F, 0.4F),
							-1.2F, 0.0F, -1.2F, 2.4F, 2.4F, 2.4F, -0.7F, -1.4F, -0.7F, 1.4F, 1.4F, 1.4F);
					p.solid(gourd, "cord", BAND_CRIMSON, zero, -0.25F, -2.6F, -0.25F, 0.5F, 1.2F, 0.5F);
				}
			}
			case SMITH -> {
				p.solid(head, "headband", BAND_CRIMSON, zero, -4.35F, -6.7F, -4.35F, 8.7F, 1.2F, 8.7F);
				p.solid(head, "knot", BAND_CRIMSON, PartPose.offsetAndRotation(0.0F, -6.2F, 4.3F, rad(25.0F), 0.0F, 0.0F),
						-1.2F, 0.0F, -0.2F, 0.9F, 3.2F, 0.4F, 0.3F, 0.0F, -0.2F, 0.9F, 2.6F, 0.4F);
				p.solid(body, "apron", BAND_LEATHER, zero, -3.8F, 1.0F, -2.6F, 7.6F, 11.6F, 0.5F, -3.6F, -0.2F, -2.45F, 1.0F, 1.4F, 4.9F,
						2.6F, -0.2F, -2.45F, 1.0F, 1.4F, 4.9F);
				p.solid(body, "belt", BAND_LEATHER, zero, -4.3F, 7.0F, -2.3F, 8.6F, 1.2F, 4.6F);
				p.solid(body, "buckle", BAND_BRONZE, zero, -0.9F, 6.9F, -2.75F, 1.8F, 1.4F, 0.4F);
				p.solid(rightArm, "bracer", BAND_LEATHER, zero, -3.35F, 5.6F, -2.35F, 4.7F, 4.0F, 4.7F);
				p.solid(leftArm, "bracer", BAND_LEATHER, zero, -1.35F, 5.6F, -2.35F, 4.7F, 4.0F, 4.7F);
			}
			case GUARD -> {
				p.solid(head, "helm", BAND_STEEL, zero, -4.6F, -9.2F, -4.6F, 9.2F, 4.6F, 9.2F, -4.7F, -4.8F, -3.0F, 0.6F, 3.6F, 4.6F,
						4.1F, -4.8F, -3.0F, 0.6F, 3.6F, 4.6F, -4.6F, -5.0F, 3.5F, 9.2F, 4.2F, 1.1F);
				p.solid(head, "brow", BAND_GOLD, zero, -4.75F, -5.4F, -4.75F, 9.5F, 0.6F, 9.5F);
				p.solid(head, "crest", BAND_GOLD, zero, -0.6F, -10.3F, -4.8F, 1.2F, 1.4F, 9.6F);
				p.solid(head, "spike", BAND_GOLD, zero, -0.5F, -11.8F, -0.5F, 1.0F, 1.6F, 1.0F);
				p.solid(head, "plume", BAND_CRIMSON, PartPose.offsetAndRotation(0.0F, -11.6F, 0.6F, rad(-20.0F), 0.0F, 0.0F),
						-0.7F, -4.6F, -0.7F, 1.4F, 4.6F, 3.4F);
				p.solid(body, "cuirass", BAND_STEEL, zero, -4.45F, 0.4F, -2.65F, 8.9F, 7.6F, 0.6F, -4.45F, 0.4F, 2.05F, 8.9F, 7.6F, 0.6F,
						-4.45F, 8.0F, -2.6F, 8.9F, 4.4F, 0.5F, -4.45F, 8.0F, 2.1F, 8.9F, 4.4F, 0.5F);
				p.solid(body, "mirror", BAND_GOLD, zero, -1.6F, 2.0F, -3.0F, 3.2F, 3.2F, 0.4F);
				p.solid(body, "belt", BAND_LEATHER, zero, -4.4F, 7.2F, -2.4F, 8.8F, 1.2F, 4.8F);
				p.tinted(body, "tabard", zero, -2.3F, 8.3F, -2.8F, 4.6F, 4.8F, 0.3F);
				p.solid(rightArm, "pauldron", BAND_STEEL, PartPose.rotation(0.0F, 0.0F, rad(8.0F)), -3.6F, -2.7F, -2.6F, 5.2F, 3.6F, 5.2F);
				p.solid(leftArm, "pauldron", BAND_STEEL, PartPose.rotation(0.0F, 0.0F, rad(-8.0F)), -1.6F, -2.7F, -2.6F, 5.2F, 3.6F, 5.2F);
			}
			case FOLK_HAT -> {
				p.solid(head, "straw", BAND_STRAW, zero, -6.0F, -8.9F, -6.0F, 12.0F, 0.8F, 12.0F, -3.6F, -10.0F, -3.6F, 7.2F, 1.2F, 7.2F,
						-1.4F, -10.8F, -1.4F, 2.8F, 0.8F, 2.8F);
				p.tinted(head, "ribbon", zero, -3.7F, -9.3F, -3.7F, 7.4F, 0.5F, 7.4F);
				p.tinted(body, "sash", zero, -4.35F, 6.6F, -2.35F, 8.7F, 1.4F, 4.7F);
			}
			case FOLK_SCARF -> {
				p.tinted(head, "scarf", zero, -4.4F, -8.7F, -4.4F, 8.8F, 2.8F, 8.8F, -1.0F, -6.4F, 4.2F, 2.0F, 4.2F, 0.4F);
				p.tinted(body, "sash", zero, -4.35F, 6.6F, -2.35F, 8.7F, 1.4F, 4.7F);
				PartDefinition basket = p.solid(body, "basket", BAND_STRAW, PartPose.offset(5.0F, 8.4F, 0.0F), -1.6F, 0.0F, -1.8F, 3.2F, 2.6F, 3.6F);
				p.solid(basket, "handle", BAND_WOOD, zero, -0.3F, -2.4F, -0.3F, 0.6F, 2.4F, 0.6F);
			}
			case FOLK_BUN -> {
				p.solid(head, "bun", BAND_HAIR, zero, -1.8F, -10.8F, -0.6F, 3.6F, 2.8F, 3.6F);
				p.solid(head, "pin", BAND_JADE, PartPose.offsetAndRotation(0.0F, -9.8F, 1.2F, 0.0F, rad(-25.0F), 0.0F),
						-3.0F, -0.3F, -0.3F, 6.0F, 0.6F, 0.6F);
				p.tinted(body, "sash", zero, -4.35F, 6.6F, -2.35F, 8.7F, 1.4F, 4.7F);
			}
			case FOLK_KNOT -> {
				p.solid(head, "knot", BAND_HAIR, zero, -1.2F, -10.6F, -1.2F, 2.4F, 2.6F, 2.4F);
				p.solid(head, "wrap", BAND_WHITE, zero, -1.5F, -9.6F, -1.5F, 3.0F, 1.0F, 3.0F);
				p.tinted(body, "sash", zero, -4.35F, 6.6F, -2.35F, 8.7F, 1.4F, 4.7F);
				p.solid(body, "pouch", BAND_LEATHER, zero, 2.6F, 8.0F, -2.8F, 1.6F, 2.0F, 0.9F);
			}
			case WANDERER_DOULI -> {
				p.solid(head, "douli", BAND_STRAW, zero, -7.5F, -8.8F, -7.5F, 15.0F, 0.8F, 15.0F, -5.4F, -9.6F, -5.4F, 10.8F, 0.8F, 10.8F,
						-3.4F, -10.4F, -3.4F, 6.8F, 0.8F, 6.8F, -1.4F, -11.2F, -1.4F, 2.8F, 0.8F, 2.8F);
				p.solid(head, "veil", BAND_GAUZE, zero, -7.3F, -8.0F, -4.0F, 0.2F, 5.5F, 8.0F, 7.1F, -8.0F, -4.0F, 0.2F, 5.5F, 8.0F,
						-5.0F, -8.0F, 7.1F, 10.0F, 6.0F, 0.2F);
				p.solid(head, "tie", BAND_CRIMSON, zero, -0.4F, -8.0F, -4.45F, 0.8F, 0.8F, 0.3F);
				p.tinted(body, "cloak", PartPose.rotation(rad(6.0F), 0.0F, 0.0F), -4.6F, -0.2F, 2.1F, 9.2F, 13.0F, 0.6F);
				p.tinted(body, "belt", zero, -4.35F, 6.8F, -2.35F, 8.7F, 1.3F, 4.7F);
				hipSword(p, body);
				PartDefinition gourd = p.solid(body, "gourd", BAND_GOURD, PartPose.offset(-4.4F, 8.4F, 1.0F),
						-1.3F, 0.0F, -1.3F, 2.6F, 2.6F, 2.6F, -0.8F, -1.5F, -0.8F, 1.6F, 1.5F, 1.6F);
				p.solid(gourd, "cord", BAND_CRIMSON, zero, -0.25F, -2.7F, -0.25F, 0.5F, 1.2F, 0.5F);
			}
			case WANDERER_GUAN -> {
				p.solid(head, "knot", BAND_HAIR, zero, -1.3F, -10.8F, -1.3F, 2.6F, 2.8F, 2.6F);
				p.solid(head, "guan", BAND_JADE, zero, -1.6F, -11.6F, -1.6F, 3.2F, 1.6F, 3.2F);
				p.solid(head, "pin", BAND_GOLD, zero, -2.7F, -11.1F, -0.3F, 5.4F, 0.6F, 0.6F);
				p.tinted(head, "ribbon_l", PartPose.offsetAndRotation(0.8F, -10.0F, 1.4F, rad(18.0F), 0.0F, rad(-4.0F)), -0.6F, 0.0F, 0.0F, 1.2F, 9.0F, 0.2F);
				p.tinted(head, "ribbon_r", PartPose.offsetAndRotation(-0.8F, -10.0F, 1.4F, rad(18.0F), 0.0F, rad(4.0F)), -0.6F, 0.0F, 0.0F, 1.2F, 8.0F, 0.2F);
				p.tinted(body, "belt", zero, -4.35F, 6.8F, -2.35F, 8.7F, 1.3F, 4.7F);
				p.solid(body, "strap", BAND_LEATHER, PartPose.offsetAndRotation(0.0F, 5.0F, -2.45F, 0.0F, 0.0F, rad(-42.0F)), -0.4F, -7.5F, -0.2F, 0.8F, 15.0F, 0.4F);
				PartDefinition back = p.solid(body, "scabbard", BAND_WOOD, PartPose.offsetAndRotation(0.0F, 6.0F, 2.6F, 0.0F, 0.0F, rad(40.0F)),
						-0.6F, -5.5F, -0.6F, 1.2F, 12.5F, 1.2F);
				p.solid(back, "chape", BAND_GOLD, zero, -0.7F, 6.4F, -0.7F, 1.4F, 1.0F, 1.4F);
				PartDefinition hilt = p.solid(back, HILT, BAND_GOLD, zero, -1.3F, -6.2F, -0.9F, 2.6F, 0.7F, 1.8F, -0.6F, -10.4F, -0.6F, 1.2F, 0.6F, 1.2F);
				p.solid(hilt, "grip", BAND_BLACK, zero, -0.45F, -9.8F, -0.45F, 0.9F, 3.6F, 0.9F);
				p.solid(hilt, "tassel", BAND_CRIMSON, zero, -0.2F, -10.4F, 0.4F, 0.4F, 2.2F, 0.4F);
			}
			case RIVAL, RIVAL_LEADER -> {
				if (kind == RIVAL_LEADER) {
					p.solid(head, "knot", BAND_HAIR, zero, -1.3F, -10.6F, -1.3F, 2.6F, 2.6F, 2.6F);
					p.solid(head, "guan", BAND_GOLD, zero, -2.0F, -11.6F, -2.0F, 4.0F, 2.2F, 4.0F, -2.6F, -11.0F, -0.4F, 5.2F, 0.6F, 0.8F);
					p.solid(head, "gem", BAND_CRIMSON, zero, -0.5F, -11.2F, -2.3F, 1.0F, 1.0F, 0.4F);
					p.tinted(body, "cloak", PartPose.rotation(rad(5.0F), 0.0F, 0.0F), -4.7F, -0.4F, 2.1F, 9.4F, 13.5F, 0.6F,
							-4.7F, -0.6F, -2.6F, 9.4F, 1.2F, 5.2F);
				} else {
					p.tinted(head, "band", zero, -4.35F, -7.1F, -4.35F, 8.7F, 1.4F, 8.7F);
					p.tinted(head, "tail_l", PartPose.offsetAndRotation(0.6F, -6.6F, 4.3F, rad(35.0F), 0.0F, rad(-10.0F)), -0.4F, 0.0F, -0.15F, 0.8F, 4.2F, 0.3F);
					p.tinted(head, "tail_r", PartPose.offsetAndRotation(-0.6F, -6.6F, 4.3F, rad(30.0F), 0.0F, rad(12.0F)), -0.4F, 0.0F, -0.15F, 0.8F, 3.6F, 0.3F);
					p.solid(leftArm, "guard", BAND_LEATHER, zero, -1.4F, -2.5F, -2.4F, 4.8F, 3.0F, 4.8F);
				}
				p.solid(body, "belt", BAND_LEATHER, zero, -4.3F, 7.0F, -2.3F, 8.6F, 1.2F, 4.6F);
				hipSword(p, body);
			}
			case IMMORTAL -> {
				p.solid(head, "guan", BAND_JADE, zero, -1.8F, -11.8F, -1.8F, 3.6F, 3.0F, 3.6F);
				p.solid(head, "pin", BAND_GOLD, zero, -4.2F, -10.8F, -0.4F, 8.4F, 0.8F, 0.8F);
				p.solid(head, "hair", BAND_HAIR, zero, -4.3F, -8.6F, -4.3F, 8.6F, 1.4F, 8.6F, -4.3F, -8.0F, 3.3F, 8.6F, 11.0F, 1.1F);
				p.tinted(body, "shawl", zero, -4.6F, -0.5F, -2.6F, 9.2F, 1.2F, 5.2F);
				p.tinted(rightArm, "ribbon", PartPose.rotation(rad(-10.0F), 0.0F, rad(14.0F)), -3.7F, 3.0F, -0.2F, 0.3F, 11.0F, 0.5F);
				p.tinted(leftArm, "ribbon", PartPose.rotation(rad(-10.0F), 0.0F, rad(-14.0F)), 3.4F, 3.0F, -0.2F, 0.3F, 11.0F, 0.5F);
				p.solid(body, "pendant", BAND_JADE, zero, -0.8F, 7.8F, -2.7F, 1.6F, 2.4F, 0.4F);
				p.solid(body, "belt", BAND_GOLD, zero, -4.35F, 7.0F, -2.35F, 8.7F, 0.8F, 4.7F);
			}
			case SAGE -> {
				p.solid(head, "hat", BAND_BLACK, zero, -3.4F, -12.6F, -3.4F, 6.8F, 4.6F, 6.8F);
				p.solid(head, "hat_band", BAND_GOLD, zero, -3.6F, -9.2F, -3.6F, 7.2F, 0.7F, 7.2F);
				p.solid(head, "beard", BAND_WHITE, zero, -2.3F, -1.6F, -4.7F, 4.6F, 7.6F, 1.0F, -2.7F, -2.7F, -4.6F, 5.4F, 0.8F, 0.6F);
				p.solid(head, "brows", BAND_WHITE, zero, -3.6F, -5.8F, -4.45F, 2.8F, 0.7F, 0.4F, 0.8F, -5.8F, -4.45F, 2.8F, 0.7F, 0.4F);
				p.tinted(body, "sash", zero, -4.35F, 6.6F, -2.35F, 8.7F, 1.4F, 4.7F);
				PartDefinition whisk = p.solid(body, "whisk", BAND_WOOD, PartPose.offsetAndRotation(-4.6F, 4.0F, -1.2F, rad(-8.0F), 0.0F, rad(10.0F)),
						-0.4F, 0.0F, -0.4F, 0.8F, 5.0F, 0.8F);
				p.solid(whisk, "tail", BAND_WHITE, zero, -0.9F, 5.0F, -0.9F, 1.8F, 6.0F, 1.8F);
			}
			case ENVOY -> {
				p.solid(head, "hat", BAND_BLACK, zero, -4.5F, -10.6F, -4.5F, 9.0F, 2.8F, 9.0F, -3.5F, -12.2F, -1.0F, 7.0F, 1.6F, 5.0F);
				p.solid(head, "wing_l", BAND_GOLD, PartPose.offset(4.4F, -11.0F, 2.6F), 0.0F, -0.5F, -1.2F, 8.5F, 1.0F, 2.4F);
				p.solid(head, "wing_r", BAND_GOLD, PartPose.offset(-4.4F, -11.0F, 2.6F), -8.5F, -0.5F, -1.2F, 8.5F, 1.0F, 2.4F);
				p.solid(head, "jewel", BAND_JADE, zero, -0.8F, -10.2F, -4.8F, 1.6F, 1.6F, 0.4F);
				p.solid(body, "belt", BAND_JADE, zero, -4.45F, 7.0F, -2.45F, 8.9F, 1.4F, 4.9F);
				p.solid(body, "plaques", BAND_GOLD, zero, -3.4F, 7.1F, -2.65F, 1.4F, 1.2F, 0.3F, -0.7F, 7.1F, -2.65F, 1.4F, 1.2F, 0.3F,
						2.0F, 7.1F, -2.65F, 1.4F, 1.2F, 0.3F);
				p.tinted(body, "collar", zero, -4.5F, -0.4F, -2.55F, 9.0F, 1.6F, 5.1F);
			}
			case GARDEN -> {
				p.solid(head, "crown", BAND_JADE, zero, -4.5F, -8.6F, -4.5F, 9.0F, 0.9F, 9.0F);
				p.solid(head, "blossoms", BAND_PEACH, zero, -4.7F, -9.4F, -2.6F, 1.4F, 1.4F, 1.4F, 3.3F, -9.4F, -0.4F, 1.4F, 1.4F, 1.4F,
						-1.6F, -9.5F, -4.8F, 1.6F, 1.6F, 1.4F, 0.8F, -9.4F, 3.4F, 1.4F, 1.4F, 1.4F, -4.6F, -9.3F, 2.0F, 1.2F, 1.2F, 1.2F,
						2.6F, -9.5F, -4.7F, 1.4F, 1.4F, 1.4F);
				PartDefinition basket = p.solid(body, "basket", BAND_STRAW, PartPose.offset(5.3F, 8.4F, 0.0F), -1.8F, 0.0F, -1.9F, 3.6F, 2.6F, 3.8F);
				p.solid(basket, "handle", BAND_WOOD, zero, -0.3F, -2.4F, -0.3F, 0.6F, 2.4F, 0.6F);
				p.solid(basket, "peaches", BAND_PEACH, zero, -1.3F, -0.8F, -1.4F, 1.4F, 1.2F, 1.4F, -0.1F, -0.7F, 0.1F, 1.3F, 1.1F, 1.3F);
				p.tinted(body, "sash", zero, -4.35F, 6.6F, -2.35F, 8.7F, 1.4F, 4.7F);
			}
			default -> {
			}
		}
		return LayerDefinition.create(mesh, 64, 512);
	}

	/** The sword in its sheath at the left hip, tip back and down, the hilt forward and up (hidden while drawn). */
	private static void hipSword(Pass p, PartDefinition body) {
		PartPose zero = PartPose.ZERO;
		PartDefinition sheath = p.solid(body, "scabbard", BAND_WOOD, PartPose.offsetAndRotation(4.7F, 7.4F, -0.4F, rad(55.0F), 0.0F, rad(6.0F)),
				-0.6F, 0.0F, -0.9F, 1.2F, 11.0F, 1.8F);
		p.solid(sheath, "chape", BAND_GOLD, zero, -0.75F, 10.0F, -1.05F, 1.5F, 1.2F, 2.1F, -0.75F, 3.0F, -1.05F, 1.5F, 0.6F, 2.1F);
		PartDefinition hilt = p.solid(sheath, HILT, BAND_GOLD, zero, -1.2F, -0.7F, -1.4F, 2.4F, 0.7F, 2.8F, -0.6F, -4.9F, -0.6F, 1.2F, 0.6F, 1.2F);
		p.solid(hilt, "grip", BAND_BLACK, zero, -0.45F, -4.3F, -0.45F, 0.9F, 3.6F, 0.9F);
		p.solid(hilt, "tassel", BAND_CRIMSON, zero, -0.2F, -4.9F, 0.5F, 0.4F, 2.4F, 0.4F);
	}

	private KotaGear() {
	}
}
