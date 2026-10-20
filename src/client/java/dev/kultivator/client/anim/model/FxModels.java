package dev.kultivator.client.anim.model;

import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The table of 3D technique props (polish plan 3.8): per model id its usual scale, which way it points, whether it is
 * lit full bright, the colours of its particle stand-in (Minimal quality, a refused display) and its child parts (wings
 * that flap, clock hands that turn, a gourd's stopper). The models themselves are {@code assets/kultivator/items/fx/<id>
 * .json}, made by {@code tools/gen_fx_model_textures.py}: every model is anchored at its (8, 8, 8) point (the centre of
 * floating props, the base of standing ones, the grip of weapons), its front faces north and its "point" is either up
 * (+y: swords, staffs, banners, fingers) or north (needles, arrows, spears, beasts). Unknown ids get neutral defaults.
 */
public final class FxModels {

	/** Where the model points: UP (+y, swords and staffs) or NORTH (its front: projectiles, beasts, hands). */
	public enum Axis { UP, NORTH }

	/** A child prop riding the parent: {@code x, y, z} in model pixels from the parent's anchor, with its own motion. */
	public record Part(String model, float x, float y, float z, Anim anim, float a, float b) {
	}

	/**
	 * {@code scale}: the usual display scale; {@code baseY}: the lowest point below the anchor in blocks at scale 1 (for
	 * standing a model on the ground: lift it by {@code -baseY * scale}); {@code color}/{@code color2}: the particle hint.
	 */
	public record Info(float scale, Axis axis, boolean glow, int color, int color2, float baseY, List<Part> parts) {
	}

	private static final Info DEFAULT = new Info(1.0F, Axis.NORTH, true, 0xFFE88A, 0xFFFFFF, -0.5F, List.of());
	private static final Map<String, Info> INFO = new HashMap<>();

	private static void put(String id, float scale, Axis axis, boolean glow, int color, int color2, float baseY, Part... parts) {
		INFO.put(id, new Info(scale, axis, glow, color, color2, baseY, List.of(parts)));
	}

	private static Part wing(String model, float x, float y, float z, float degrees, float period) {
		return new Part(model, x, y, z, Anim.FLAP, degrees, period);
	}

	static {
		Axis up = Axis.UP;
		Axis north = Axis.NORTH;
		// weapons (grip at the anchor, blade up)
		put("giant_sword", 8.0F, up, true, 0xD8E6FF, 0x7C98D8, -0.3F);
		put("light_sword", 1.5F, up, true, 0xD8E6FF, 0xFFFFFF, -0.4F);
		put("bloodmoon_sword", 6.0F, up, true, 0xC0182A, 0x4A0008, -0.3F);
		put("star_sword", 2.0F, up, true, 0xE8F0FF, 0x6A8AD8, -0.3F);
		put("grass_blade_sword", 1.5F, up, true, 0x6CFF8A, 0x2E8A3A, -0.3F);
		put("crystal_blade", 3.0F, up, true, 0x9FF3FF, 0xFFFFFF, -0.3F);
		put("heaven_punishing_sword", 24.0F, up, true, 0xFFD54A, 0xC7A2FF, -0.3F);
		put("zhuxian_sword", 10.0F, up, true, 0xFF6A2A, 0xFFFFFF, -0.3F);
		put("luxian_sword", 10.0F, up, true, 0x6CFF8A, 0xFFFFFF, -0.3F);
		put("xianxian_sword", 10.0F, up, true, 0x9FF3FF, 0xFFFFFF, -0.3F);
		put("juexian_sword", 10.0F, up, true, 0xB57CFF, 0xFFFFFF, -0.3F);
		put("xuantian_sword", 16.0F, up, true, 0xFFD54A, 0x2A2A33, -0.3F);
		put("natal_sword", 1.0F, up, false, 0x6CFF8A, 0x5FD6A0, -0.3F);
		put("natal_sword_silver", 1.0F, up, false, 0xC8D0DC, 0xFFFFFF, -0.3F);
		put("natal_sword_gold", 1.0F, up, false, 0xFFD54A, 0xFFF2B0, -0.3F);
		put("thunder_sword", 6.0F, up, true, 0xC7A2FF, 0xFFFFFF, -0.3F);
		put("ice_spear", 1.5F, north, true, 0x9FF3FF, 0xFFFFFF, -0.1F);
		put("needle", 0.6F, north, true, 0xE8ECF4, 0xFFFFFF, -0.05F);
		put("phoenix_arrow", 1.0F, north, true, 0xFF7A1A, 0xFFF6C0, -0.1F);
		put("ruyi_staff", 1.0F, up, true, 0xC0201A, 0xFFD54A, -1.25F);
		put("ruyi_staff_gold", 1.0F, up, true, 0xFFD54A, 0xFFF2B0, -1.25F);
		put("heavy_ruler", 6.0F, up, false, 0x2A2A33, 0xFFD54A, -1.4F);
		put("wind_blade", 1.0F, north, true, 0xE8F0FF, 0xFFFFFF, -0.2F);
		put("sumeru_hammer", 6.0F, up, true, 0xFFD54A, 0xFFF2B0, -1.0F);
		put("dharma_hammer", 3.0F, up, false, 0xA86A32, 0xFFD54A, -1.0F);
		// hands and feet (palm faces north, fingers up)
		put("buddha_palm", 12.0F, north, true, 0xFFD54A, 0xFFF2B0, -0.8F);
		put("qi_palm", 3.0F, north, true, 0xFFD54A, 0xFFFFFF, -0.8F);
		put("jade_palm", 1.2F, north, true, 0x5FD6A0, 0xB8FFD8, -0.8F);
		put("thunder_palm", 8.0F, north, true, 0xFFD54A, 0xC7A2FF, -0.8F);
		put("prison_hand", 9.0F, north, true, 0xA86A32, 0xFFE28A, -0.8F);
		put("prison_hand_open", 9.0F, north, true, 0xA86A32, 0xFFE28A, -0.8F);
		put("prison_hand_closed", 9.0F, north, true, 0xA86A32, 0xFFE28A, -0.8F);
		put("golden_fist", 1.6F, north, true, 0xFFD54A, 0xFFF2B0, -0.7F);
		put("emperor_fist", 3.0F, north, true, 0xFFD54A, 0xC02A20, -0.7F);
		put("phantom_fist", 2.0F, north, true, 0xFFE88A, 0xFFFFFF, -0.7F);
		put("fate_finger", 6.0F, up, true, 0xFFFFFF, 0xEEF2F8, -1.1F);
		put("god_foot", 8.0F, north, false, 0xA86A32, 0xFFD54A, 0.0F);
		// beasts
		put("white_tiger", 3.0F, north, true, 0xE8E8EE, 0xB060FF, -0.45F);
		put("tiger_orange", 1.0F, north, false, 0xE07820, 0x2A2010, -0.45F);
		put("sky_wolf", 3.0F, north, true, 0x8A96B0, 0x8AF0FF, -0.45F);
		put("fire_phoenix", 8.0F, north, true, 0xFF7A1A, 0xFFF6C0, -0.2F,
				wing("fire_phoenix_wing_r", 3.0F, 1.5F, -1.0F, 32.0F, 12.0F), wing("fire_phoenix_wing_l", -3.0F, 1.5F, -1.0F, -32.0F, 12.0F));
		put("ice_phoenix", 8.0F, north, true, 0xBFF6FF, 0xFFFFFF, -0.2F,
				wing("ice_phoenix_wing_r", 3.0F, 1.5F, -1.0F, 28.0F, 14.0F), wing("ice_phoenix_wing_l", -3.0F, 1.5F, -1.0F, -28.0F, 14.0F));
		put("dragon_head", 3.0F, north, true, 0xC8281A, 0xFFD54A, -0.3F);
		put("dragon_segment", 3.0F, north, true, 0xC8281A, 0xFFD54A, -0.2F);
		put("fire_crow", 0.8F, north, true, 0x1A1A22, 0xFF9A2A, -0.1F);
		put("qilin", 4.0F, north, true, 0x5FD6A0, 0xFFD54A, -0.4F);
		put("kunpeng", 20.0F, north, false, 0x3A5A9A, 0xE8F0FF, -0.3F,
				wing("kunpeng_wing_r", 6.0F, 2.0F, 2.0F, 22.0F, 30.0F), wing("kunpeng_wing_l", -6.0F, 2.0F, 2.0F, -22.0F, 30.0F));
		put("ice_scorpion", 3.0F, north, true, 0xBFF6FF, 0xFFFFFF, -0.2F);
		put("silver_butterfly", 0.6F, north, true, 0xE8ECF4, 0xA8B8E0, -0.1F);
		put("gold_beetle", 0.5F, north, true, 0xF2B33D, 0x8A5A12, -0.15F);
		put("sleep_bug", 0.4F, north, true, 0x6CFF8A, 0xF0FFE8, -0.1F);
		// treasures and architecture (standing ones rise from the anchor)
		put("emperor_seal", 6.0F, north, true, 0x5FD6A0, 0xFFD54A, 0.0F);
		put("fantian_seal", 8.0F, north, true, 0xA86A32, 0xFFD54A, 0.0F);
		put("mountain_sea_seal", 6.0F, north, false, 0x7D7566, 0xD9A05B, 0.0F);
		put("void_mirror", 4.0F, north, true, 0xA86A32, 0x7A4AFF, -0.8F);
		put("golden_mirror", 2.0F, north, true, 0xFFD54A, 0xFFFFFF, 0.0F);
		put("thunder_drum", 2.5F, north, false, 0xB0201A, 0xFFD54A, -0.3F);
		put("natal_bell", 1.0F, up, false, 0xA86A32, 0xFFD54A, 0.0F);
		put("lu_ya_gourd", 1.2F, up, false, 0xC0BC40, 0xC02A20, 0.0F);
		put("zijin_gourd", 1.5F, up, true, 0x8A3AC8, 0xFFD54A, 0.0F, new Part("gourd_stopper", 0.0F, 18.6F, 0.0F, Anim.NONE, 0.0F, 0.0F));
		put("gourd_stopper", 1.5F, up, true, 0xFFD54A, 0xFFFFFF, -0.4F);
		put("crow_vase", 2.0F, up, false, 0x1A1A22, 0xFF9A2A, 0.0F);
		put("guanyin_vase", 3.0F, up, true, 0xEEF2F8, 0x9FF3FF, 0.0F, new Part("willow_sprig", 0.0F, 14.0F, 0.0F, Anim.SWING, 8.0F, 40.0F));
		put("willow_sprig", 2.0F, up, true, 0x3FAE3A, 0xB8FFA0, 0.0F);
		put("banana_fan", 2.5F, up, false, 0x3FAE3A, 0xFFD54A, -0.5F);
		put("qiankun_ring", 1.5F, north, true, 0xFFD54A, 0xFFF2B0, -0.5F);
		put("seed_bag", 4.0F, up, false, 0xE0B830, 0x4A3A6A, 0.0F);
		put("cymbal", 3.0F, north, true, 0xFFD54A, 0xFFF2B0, -0.1F);
		put("mother_qi_cauldron", 6.0F, north, true, 0xA86A32, 0xFF9A2A, 0.0F);
		put("bagua_furnace", 10.0F, north, true, 0xA86A32, 0xFF9A2A, 0.0F);
		put("sun", 6.0F, north, true, 0xFFD54A, 0xFF6A10, -0.85F);
		put("black_hole_core", 4.0F, north, true, 0x08060C, 0xFF8A20, -0.4F);
		put("accretion_segment", 4.0F, north, true, 0xFF8A20, 0xFFF0C0, -0.05F);
		put("accretion_disk", 4.0F, north, true, 0xFF8A20, 0xFFF0C0, -0.05F);
		put("taiji_disc", 6.0F, north, true, 0xF8F6EC, 0x16141C, -0.05F);
		put("hex_disc", 3.0F, north, true, 0x7A6AA0, 0xC8BEDC, -0.05F);
		put("time_clock", 6.0F, north, true, 0xF2C24A, 0xFFF4C8, -0.55F,
				new Part("clock_hand", 0.0F, 0.0F, 0.0F, Anim.SPIN, -6.0F, 2.0F), new Part("clock_hand_short", 0.0F, 0.0F, -0.3F, Anim.SPIN, -1.5F, 2.0F));
		put("clock_hand", 6.0F, north, true, 0x2A2A33, 0xFFD54A, -0.1F);
		put("clock_hand_short", 6.0F, north, true, 0x2A2A33, 0xFFD54A, -0.1F);
		put("six_path_wheel", 4.0F, north, true, 0xFFD54A, 0xFFFFFF, -0.85F);
		put("crescent_moon", 2.5F, north, true, 0xF4E6A8, 0xFFFDF0, -0.6F);
		put("nimbus_cloud", 1.5F, north, true, 0xFFFFFF, 0xFFD54A, -0.15F);
		put("cloud_puff", 0.6F, north, true, 0xFFFFFF, 0xEEF2F8, -0.25F);
		put("lotus_pedestal", 1.6F, north, true, 0xFF9EC8, 0xFFFFFF, 0.0F);
		put("tang_lotus", 2.0F, north, true, 0x5FD6A0, 0xFFD54A, 0.0F);
		put("fury_lotus", 1.2F, north, true, 0x5AB8FF, 0xFF9A2A, 0.0F);
		put("ice_lotus", 1.5F, north, true, 0x9FF3FF, 0xFFFFFF, 0.0F);
		put("lotus_petal", 2.0F, up, true, 0xFF9EC8, 0xFFF4F8, 0.0F);
		put("lotus_petal_violet", 2.0F, up, true, 0xB060FF, 0xF8E8FF, 0.0F);
		// Api Kaisar (emperor_flame): glass petals rising from their base (y 8..32), the seed-pod heart, its flame, the shock ring
		put("emperor_lotus_petal_outer", 24.0F, up, true, 0xA866FF, 0xFFF8FF, 0.0F);
		put("emperor_lotus_petal_inner", 16.0F, up, true, 0xD8B8FF, 0xFFFFFF, 0.0F);
		put("emperor_lotus_core", 14.0F, north, true, 0xFFF2C8, 0xC890FF, 0.0F);
		put("emperor_lotus_flame", 14.0F, up, true, 0xF0D8FF, 0xA866FF, 0.0F);
		put("emperor_shock_ring", 30.0F, north, true, 0xFFFFFF, 0xB070FF, -0.02F);
		put("emperor_shock_ring_fade", 30.0F, north, true, 0xF0E0FF, 0x9A5AE8, -0.02F);
		// Murka Langit Sembilan Matahari (nine_suns_heavens_fury): the sun, its streak, the flash ring, the crater, the crow
		put("nine_sun_orb", 5.0F, north, true, 0xFFF2B0, 0xFF8A1A, -0.6F);
		put("nine_sun_trail", 5.0F, north, true, 0xFFC83A, 0xD8380E, -0.5F);
		put("nine_sun_flash_ring", 10.0F, north, true, 0xFFFFF0, 0xFFB030, -0.02F);
		put("nine_sun_flash_ring_fade", 10.0F, north, true, 0xFFF2C0, 0xFF8A20, -0.02F);
		put("nine_sun_crater", 6.0F, north, true, 0xFF8A1A, 0x3A1208, 0.0F);
		put("nine_sun_crow", 1.6F, north, true, 0xFFF4C0, 0xE0401A, -0.25F);
		put("flame_cart", 1.5F, north, true, 0xA86A32, 0xFF9A2A, 0.0F);
		put("ice_mountain", 12.0F, north, true, 0x9FF3FF, 0xFFFFFF, 0.0F);
		put("ice_block", 1.2F, north, true, 0x9FF3FF, 0xFFFFFF, 0.0F);
		put("mountain_peak", 8.0F, north, false, 0x7D7566, 0xE8E4DA, 0.0F);
		put("pagoda_tier", 1.0F, north, true, 0xB0201A, 0xFFD54A, 0.0F);
		put("stupa", 4.0F, north, true, 0xE8E4DA, 0xFFD54A, 0.0F);
		put("qilin_palace", 6.0F, north, true, 0xB0201A, 0xFFD54A, 0.0F);
		put("small_temple", 1.0F, north, false, 0xB0201A, 0xFFD54A, 0.0F);
		put("bodhi_tree", 12.0F, north, true, 0x3FAE3A, 0xFFD54A, 0.0F);
		put("willow_god", 8.0F, north, false, 0x2A2018, 0x3FAE3A, 0.0F);
		put("willow_leaf", 1.0F, up, true, 0xE8C040, 0xFFF8C0, 0.0F);
		put("blue_silver_grass", 1.5F, up, true, 0x7AA8E8, 0xF0F8FF, 0.0F);
		put("straw_figure", 1.5F, up, false, 0xC8A850, 0xC02A20, 0.0F);
		put("soul_flag", 4.0F, up, true, 0x24202C, 0x4AA8FF, 0.0F);
		put("formation_banner", 2.5F, up, false, 0xC02A20, 0xFFD54A, 0.0F);
		put("yin_tiger_seal", 1.0F, north, true, 0x2A2A33, 0xFF6A2A, 0.0F);
		put("talisman_ling", 1.5F, up, true, 0xF0D890, 0xC0182A, -0.5F);
		put("thunder_ancestral_symbol", 6.0F, north, true, 0xC7A2FF, 0xFFFFFF, -0.55F);
		put("space_symbol", 6.0F, north, true, 0x5A3FD0, 0xC8B8FF, -0.55F);
		put("glyph_ring", 2.0F, north, true, 0xFFD54A, 0xFFFFFF, -0.05F);
		put("golden_hair", 0.5F, north, true, 0xF0B820, 0xFFF6B0, -0.25F);
		put("iron_plate", 0.6F, north, false, 0x2A2A33, 0xFFD54A, -0.35F);
		put("fusion_orb", 1.5F, north, true, 0xFF6A2A, 0x9FF3FF, -0.25F);
		put("restriction_seal", 7.0F, north, true, 0xA060FF, 0xE8D8FF, -0.5F);
		put("heavenly_flame_orb", 0.5F, north, true, 0xFF9A2A, 0xFFFFFF, -0.15F);
		put("heavenly_flame_seed", 0.5F, north, true, 0xFF9A2A, 0xFFFFFF, -0.2F);
		put("chain_link", 0.5F, north, true, 0xFFD54A, 0xFFF2B0, -0.2F);
		put("seal_ring_feng", 1.5F, north, true, 0xFF6A2A, 0xFFE0D8, -0.05F);
		put("ice_claw", 1.0F, north, true, 0x9FF3FF, 0xFFFFFF, -1.1F);
		put("missing", 1.0F, north, true, 0xFFD54A, 0xFFFFFF, -0.25F);
	}

	/** The child parts of exactly this model id (a family's variants and pieces get none). */
	public static List<Part> parts(@Nullable String id) {
		Info info = id == null ? null : INFO.get(id);
		return info == null ? List.of() : info.parts();
	}

	/** The info of a model id (variants such as {@code qi_palm_fire} share their family's entry; never null). */
	public static Info info(@Nullable String id) {
		if (id == null) {
			return DEFAULT;
		}
		Info info = INFO.get(id);
		if (info != null) {
			return info;
		}
		int cut = id.lastIndexOf('_');
		while (cut > 0) {
			info = INFO.get(id.substring(0, cut));
			if (info != null) {
				return info;
			}
			cut = id.lastIndexOf('_', cut - 1);
		}
		return DEFAULT;
	}

	private FxModels() {
	}
}
