package dev.kultivator.client.fx.spec;

import dev.kultivator.client.ClientCultivation;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Technique;
import dev.kultivator.data.VisualState;
import dev.kultivator.technique.spec.dsl.FxLayer;
import dev.kultivator.technique.spec.dsl.FxLayer.Template;
import dev.kultivator.technique.spec.dsl.Phase;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;

/**
 * The visual tier layers of the 23 legacy techniques (DESIGN 22.10, column "Visual (tier)"). Their old
 * {@code FxType.TECHNIQUE} sub-effects keep drawing tiers 0-1; these layers come on top from the legacy spec's
 * {@code TechFx} CAST event (spec = ordinal, pos = the caster, second point = the aim or end point, radius and length =
 * the scaled sizes, duration = lasting forms and domains), each gated by its {@code minTier}: glyphs from 2, phantom
 * displays from 3, sky effects from 4, the Dewa shimmer from 5 (22.4.7).
 */
final class LegacyLayers {

	private static final int JADE = 0xCFF6FF;
	private static final int GOLD = 0xFFD54A;
	private static final int PALE_GOLD = 0xFFF1B8;

	/** Extra layers of {@code technique} for this event (empty for phases other than CAST). */
	static List<FxLayer> layers(Technique technique, FxEvent ev) {
		if (ev.phase != Phase.CAST) {
			return List.of();
		}
		List<FxLayer> list = new ArrayList<>();
		int lasting = ev.duration > 0 ? ev.duration : 0;
		switch (technique) {
			case CLOUD_STEP -> {
				list.add(FxLayer.of(Template.SHOCK_RING, 0xF4F8FF).color2(0xDDE8F5).radius(1.2F).count(24).ticks(8).minTier(2));
				list.add(FxLayer.of(Template.MIST, GOLD).color2(PALE_GOLD).radius(1.5F).count(30).ticks(30).minTier(4).extra());
			}
			case QI_PALM -> {
				list.add(FxLayer.of(Template.PALM, JADE).color2(0xFFFFFF).radius(2.0F).count(90).ticks(12).onTarget().minTier(2));
				list.add(FxLayer.of(Template.PALM, 0xE6FFF6).color2(JADE).radius(3.5F).count(140).ticks(10).line().minTier(3).detail());
			}
			case FLYING_SWORD -> list.add(FxLayer.of(Template.SWORD_RING, JADE).item(swordOf(ev)).n(1).radius(1.4F).scale(1.2F)
					.ticks(lasting > 0 ? Math.min(600, lasting) : 600).onCaster().minTier(3));
			case DIVINE_SENSE -> {
				list.add(FxLayer.of(Template.GLYPH, 0xC9B8FF).text("識").scale(4.0F).above(3.0F).ticks(60).onCaster().minTier(3));
				list.add(FxLayer.of(Template.RUNE_CIRCLE, 0xC9B8FF).color2(0xEDE6FF).count(160).ticks(60).above(20.0F).minTier(4).extra());
			}
			case BUDDHA_PALM -> {
				list.add(FxLayer.of(Template.GLYPH, GOLD).text("佛").scale(6.0F).above(14.0F).ticks(30).onTarget().minTier(2));
				list.add(FxLayer.of(Template.PALM, GOLD).color2(PALE_GOLD).radius(Math.min(12.0F, Math.max(5.0F, ev.radius))).count(260).ticks(22)
						.falling().onTarget().minTier(3));
				list.add(FxLayer.of(Template.SHOCK_RING, GOLD).color2(PALE_GOLD).count(90).ticks(12).delay(18).onTarget().minTier(3));
				list.add(FxLayer.of(Template.CRACKS, 0x3A2A1A).count(70).ticks(10).delay(18).onTarget().minTier(3).kehancuranOnly());
				list.add(FxLayer.of(Template.SHAKE, 0).intensity(0.35F).ticks(12).delay(18).minTier(3));
			}
			case DHARMA_FORM -> {
				list.add(FxLayer.of(Template.AVATAR, GOLD).scale(4.0F).alpha(0.35F).ticks(lasting > 0 ? Math.min(900, lasting) : 600).onCaster()
						.minTier(3));
				list.add(FxLayer.of(Template.SHIELD_SHELL, 0xFF7AD9).color2(GOLD).radius(1.8F).count(400).ticks(lasting > 0 ? Math.min(900, lasting) : 600)
						.onCaster().minTier(5).extra());
			}
			case DAO_DOMAIN -> {
				Dao dao = casterDao(ev);
				int color = dao == null ? 0xE8DDB5 : dao.color();
				int ticks = lasting > 0 ? Math.min(900, lasting) : 400;
				list.add(FxLayer.of(Template.DOME, color).color2(PALE_GOLD).count(ticks * 2).ticks(ticks).minTier(2));
				list.add(FxLayer.of(Template.RUNE_CIRCLE, color).color2(0xFFFFFF).count(ticks * 2).ticks(ticks).minTier(2).detail());
				if (dao == Dao.SWORD) {
					list.add(FxLayer.of(Template.SWORD_RING, color).item(swordOf(ev)).n(8).radius(3.0F).scale(1.5F).ticks(ticks).onCaster().minTier(3));
				}
				list.add(FxLayer.of(Template.GLYPH, color).text(glyphOf(dao)).scale(10.0F).above(20.0F).ticks(Math.min(600, ticks)).minTier(4));
			}
			case SWORD_QI -> {
				list.add(FxLayer.of(Template.ARC, 0xD8E6FF).color2(0xFFFFFF).radius(4.0F).count(60).ticks(6).minTier(2));
				list.add(FxLayer.of(Template.CRESCENT_FLY, 0xD8E6FF).color2(0x7FD8FF).width(3.0F).count(120).ticks(12)
						.variant(FxLayer.Variant.VERTICAL).minTier(3));
				list.add(FxLayer.of(Template.SWORD_SHAPE, 0xFFFFFF).color2(0x7FD8FF).n(3).count(60).ticks(12).flying().minTier(4).extra());
			}
			case THOUSAND_SWORDS -> {
				list.add(FxLayer.of(Template.GIANT_ITEM, 0xFFFFFF).item(swordOf(ev)).n(8).scale(5.0F).fall(16.0F, 16.0F).ticks(40).onTarget()
						.minTier(3));
				list.add(FxLayer.of(Template.SWORD_RING, 0xD8E6FF).item(swordOf(ev)).n(8).radius(5.0F).scale(2.0F).ticks(80).above(14.0F).minTier(4)
						.extra());
			}
			case FLAME_PALM -> {
				list.add(FxLayer.of(Template.PETALS, 0xFF6A2A).color2(0xFFD54A).radius(1.4F).n(8).count(70).ticks(14).above(1.2F).minTier(2));
				list.add(FxLayer.of(Template.CONE_SPRAY, 0xFF8A3A).color2(0xFFE08A).particle(ParticleTypes.FLAME).count(90).ticks(10).above(1.4F)
						.minTier(3).detail());
			}
			case PHOENIX_FLAME -> {
				list.add(FxLayer.of(Template.WINGS, 0xE8401C).color2(0xFFB040).radius(4.0F + 2.0F * ev.tier).count(160).ticks(30).onCaster().minTier(2));
				list.add(FxLayer.of(Template.SHARDS, 0xE8401C).color2(0xFFD0A0).count(60).ticks(20).onTarget().minTier(4).extra());
			}
			case FROST_LOTUS -> {
				list.add(FxLayer.of(Template.SHOCK_RING, 0x9FF3FF).color2(0xFFFFFF).particle(ParticleTypes.SNOWFLAKE).count(70).ticks(10).minTier(2));
				list.add(FxLayer.of(Template.PETAL_RING, 0x9FF3FF).block(Blocks.STAINED_GLASS.pick(DyeColor.LIGHT_BLUE).defaultBlockState()).n(8).radius(3.0F)
						.ticks(40).minTier(3));
			}
			case GLACIER_PRISON -> {
				int hold = lasting > 0 ? Math.min(600, lasting) : 160;
				list.add(FxLayer.of(Template.GIANT_BLOCK, 0xBFE8FF).block(Blocks.PACKED_ICE.defaultBlockState()).blockSize(1.0F, 1.2F, 1.0F)
						.scale(2.2F).ticks(Math.min(100, hold)).onTarget().minTier(2));
				list.add(FxLayer.of(Template.SHARDS, 0xBFE8FF).color2(0xFFFFFF).count(80).ticks(6).delay(Math.min(100, hold)).onTarget().minTier(3));
			}
			case THUNDER_PALM -> {
				list.add(FxLayer.of(Template.BOLT, 0xB57CFF).color2(0xF0E0FF).count(70).ticks(4).line().minTier(2));
				list.add(FxLayer.of(Template.SHARDS, 0xB57CFF).particle(ParticleTypes.ELECTRIC_SPARK).count(30).ticks(6).onTarget().minTier(3).extra());
			}
			case HEAVENLY_THUNDER -> {
				list.add(FxLayer.of(Template.CLOUD_DISC, 0x2A2440).color2(0x5A4A80).radius(Math.max(8.0F, ev.radius)).count(240).ticks(50).minTier(2));
				list.add(FxLayer.of(Template.BOLT_STORM, 0xB57CFF).color2(0xFFFFFF).count(320).ticks(40).minTier(3));
				list.add(FxLayer.of(Template.SHAKE, 0).intensity(0.25F).ticks(30).minTier(3));
			}
			case IRON_BODY -> {
				int ticks = lasting > 0 ? Math.min(600, lasting) : 300;
				list.add(FxLayer.of(Template.SHIELD_SHELL, GOLD).color2(0xC0C8D0).count(Math.max(150, ticks)).ticks(ticks).onCaster().minTier(2));
				list.add(FxLayer.of(Template.SWORD_RING, 0xC0C8D0).item(Items.HEAVY_WEIGHTED_PRESSURE_PLATE).n(4).radius(1.6F).scale(1.2F)
						.ticks(Math.min(600, ticks)).onCaster().minTier(3));
			}
			case MOUNTAIN_STOMP -> {
				list.add(FxLayer.of(Template.CRACKS, 0x3A2A1A).count(80).ticks(10).minTier(2).kehancuranOnly());
				list.add(FxLayer.of(Template.SHOCK_RING, 0xB08A5A).color2(0xD9A05B).count(100).ticks(12).minTier(2));
				list.add(FxLayer.of(Template.PETAL_RING, 0x8A8A8A).block(Blocks.STONE_SLAB.defaultBlockState()).n(8)
						.radius(Math.max(3.0F, Math.min(12.0F, ev.radius))).ticks(20).minTier(3));
				list.add(FxLayer.of(Template.SHAKE, 0).intensity(0.25F).ticks(12).minTier(2));
			}
			case SPRING_OF_LIFE -> {
				list.add(FxLayer.of(Template.PETALS, 0x6CFF8A).color2(0xF0FFF4).radius(1.6F).n(10).count(80).ticks(16).minTier(2));
				list.add(FxLayer.of(Template.HEAL_RISE, 0x6CFF8A).color2(0xFFFFFF).radius(1.5F).count(90).ticks(30).minTier(3));
			}
			case LIFE_DRAIN -> {
				list.add(FxLayer.of(Template.VORTEX, 0xC0303A).color2(0x6CFF8A).inward().count(120).ticks(24).minTier(2));
				list.add(FxLayer.of(Template.DOME, 0x8A1A20).color2(0xC0303A).count(140).ticks(30).minTier(3).detail());
			}
			case SHADOW_STEP -> {
				list.add(FxLayer.of(Template.AFTERIMAGE, 0x7A6AA0).color2(0x3A2A50).count(70).ticks(24).minTier(2));
				list.add(FxLayer.of(Template.MIST, 0x5A4A80).color2(0x7A6AA0).radius(1.6F).count(50).ticks(30).onTarget().minTier(3).extra());
			}
			case GALE -> {
				list.add(FxLayer.of(Template.VORTEX, 0xE8F4F0).color2(0xBFE0D8).count(110).ticks(20).minTier(2));
				list.add(FxLayer.of(Template.SHOCK_RING, 0xE8F4F0).particle(ParticleTypes.GUST).count(16).ticks(10).minTier(3).detail());
			}
			case SPACE_BLINK -> {
				list.add(FxLayer.of(Template.SHARDS, 0x5A3FD0).color2(0xC9B8FF).count(40).ticks(5).minTier(2));
				list.add(FxLayer.of(Template.SHARDS, 0x5A3FD0).color2(0xC9B8FF).count(40).ticks(5).onTarget().minTier(2));
				list.add(FxLayer.of(Template.LASER, 0x101018).block(Blocks.STAINED_GLASS.pick(DyeColor.BLACK).defaultBlockState()).width(0.3F).ticks(40).line()
						.minTier(3));
			}
			case SPACE_PRISON -> {
				int hold = lasting > 0 ? Math.min(100, lasting) : 100;
				float size = Math.max(3.0F, Math.min(24.0F, ev.radius * 2.0F));
				list.add(FxLayer.of(Template.GIANT_BLOCK, 0x5A3FD0).block(Blocks.STAINED_GLASS.pick(DyeColor.BLACK).defaultBlockState())
						.blockSize(size, Math.min(16.0F, size * 0.6F), size).ticks(hold).minTier(2));
				list.add(FxLayer.of(Template.SHARDS, 0x5A3FD0).color2(0xC9B8FF).inward().radius(Math.max(3.0F, ev.radius)).count(90).ticks(8)
						.delay(hold).minTier(3));
			}
		}
		return list;
	}

	/** The caster's synced Dao (own data for the local player), or null. */
	private static Dao casterDao(FxEvent ev) {
		if (ev.own) {
			return ClientCultivation.data().dao();
		}
		Entity caster = ev.caster();
		VisualState visual = caster == null ? VisualState.NONE : ClientCultivation.visual(caster);
		return visual.dao() >= 0 && visual.dao() < Dao.values().length ? Dao.values()[visual.dao()] : null;
	}

	/** The caster's sword (its synced look), else an iron sword. */
	private static Item swordOf(FxEvent ev) {
		Entity caster = ev.caster();
		VisualState visual = caster == null ? VisualState.NONE : ClientCultivation.visual(caster);
		Identifier id = visual.swordItem().isEmpty() ? null : Identifier.tryParse(visual.swordItem());
		if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
			return BuiltInRegistries.ITEM.getValue(id);
		}
		return Items.IRON_SWORD;
	}

	/** The Dao glyph written in the sky over a Dao Domain (22.10). */
	static String glyphOf(Dao dao) {
		if (dao == null) {
			return "道";
		}
		return switch (dao) {
			case SWORD -> "剑";
			case FIRE -> "炎";
			case ICE -> "冰";
			case LIGHTNING -> "雷";
			case BODY -> "体";
			case LIFE -> "生";
			case SHADOW -> "影";
			case SPACE -> "空";
		};
	}

	private LegacyLayers() {
	}
}
