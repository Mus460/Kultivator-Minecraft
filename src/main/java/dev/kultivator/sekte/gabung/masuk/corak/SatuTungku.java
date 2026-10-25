package dev.kultivator.sekte.gabung.masuk.corak;

import dev.kultivator.alchemy.AlchemyFurnace;
import dev.kultivator.alchemy.PillQuality;
import dev.kultivator.sekte.gabung.api.Corak;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * ALKIMIA "Satu Tungku" (DESIGN 41.5.4; the furnace sects: one furnace, one pill, ten minutes): refine any pill at a
 * furnace of the sect (a cauldron on a lit campfire, {@link AlchemyFurnace#isFurnace}: the {@code kerja:kuali} of the
 * alchemy pavilion; the nearest within 16 blocks of the test spot is pointed out) with the ingredients of the test box:
 * two sets of the Qi Gathering pill (glowstone dust, sugar, glow berries) and one of the Healing pill (glistering melon,
 * golden carrot, sweet berries, kelp). Unused ingredients go back at the end. No furnace near: the box also holds a
 * cauldron and a campfire, the candidate builds one (the player's own blocks, as in any refining).
 * <p>
 * The first successful refining of this player within 48 blocks ends the test: {@code S2} by quality, Low 0.5, Mid 0.75,
 * High 0.9, Perfect 1.0 (a mix without quality 0.5). A failed refining only costs time; the second set allows a retry.
 * Limit 600 s.
 */
public final class SatuTungku extends UjianDasar {

	public static final int BATAS = 600 * 20;
	private static final Item[] QI = {Items.GLOWSTONE_DUST, Items.SUGAR, Items.GLOW_BERRIES};
	private static final Item[] SEMBUH = {Items.GLISTERING_MELON_SLICE, Items.GOLDEN_CARROT, Items.SWEET_BERRIES, Items.KELP};

	private @Nullable BlockPos tungku;
	private int coba;
	private int mutuTerbaik;

	public SatuTungku(ServerPlayer pemain, String sekte, int kelas, Vec3 pusat, long benih) {
		super(pemain, sekte, Corak.ALKIMIA, kelas, pusat, benih);
	}

	@Override
	public int batasTick() {
		return BATAS;
	}

	@Override
	public double jariWilayah() {
		return 48.0D;
	}

	@Override
	public @Nullable Component siapkan() {
		tungku = cariTungku(BlockPos.containing(pusat), 16);
		if (tungku == null) {
			tungku = cariTungku(pemain.blockPosition(), 12);
		}
		Component lore = t(ChatFormatting.GRAY, "satu_tungku.lore");
		for (Item i : QI) {
			beri(itemUjian(new ItemStack(i, 2), "bahan", null, lore));
		}
		for (Item i : SEMBUH) {
			beri(itemUjian(new ItemStack(i, 1), "bahan", null, lore));
		}
		if (tungku == null) {
			beri(itemUjian(new ItemStack(Items.CAULDRON), "bahan", null, lore));
			beri(itemUjian(new ItemStack(Items.CAMPFIRE), "bahan", null, lore));
			chat(t(ChatFormatting.GOLD, "satu_tungku.mulai_bangun"));
		} else {
			chat(t(ChatFormatting.GOLD, "satu_tungku.mulai", tungku.getX(), tungku.getY(), tungku.getZ()));
		}
		return null;
	}

	/** The nearest furnace within {@code r} blocks (horizontal) and 4 up/down of {@code c}; only reads blocks. */
	private @Nullable BlockPos cariTungku(BlockPos c, int r) {
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				for (int dy = -4; dy <= 4; dy++) {
					m.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
					if (AlchemyFurnace.isFurnace(level, m)) {
						double d = m.distSqr(c);
						if (d < bestD) {
							bestD = d;
							best = m.immutable();
						}
					}
				}
			}
		}
		return best;
	}

	@Override
	public void tik() {
		if (umur % 200 == 0 && tungku != null && !AlchemyFurnace.isFurnace(level, tungku)) {
			tungku = cariTungku(pemain.blockPosition(), 8);
		}
		if (tungku == null && umur % 100 == 0) {
			tungku = cariTungku(pemain.blockPosition(), 8);
		}
	}

	@Override
	public void pilSelesai(ServerLevel tempat, BlockPos pos, ItemStack hasil, @Nullable PillQuality mutu, boolean sukses) {
		if (tempat != level || pos.distToCenterSqr(pusat) > 48.0D * 48.0D) {
			return;
		}
		coba++;
		if (!sukses || hasil.isEmpty()) {
			bunyi(Vec3.atCenterOf(pos), SoundEvents.CHAIN_BREAK, 0.8F, 0.6F);
			bar(t(ChatFormatting.RED, "satu_tungku.gagal", coba));
			return;
		}
		double nilai = mutu == null ? 0.5D : switch (mutu) {
			case LOW -> 0.5D;
			case MID -> 0.75D;
			case HIGH -> 0.9D;
			case PERFECT -> 1.0D;
		};
		mutuTerbaik = mutu == null ? 1 : mutu.ordinal() + 1;
		skor = nilai;
		bunyi(Vec3.atCenterOf(pos), SoundEvents.BREWING_STAND_BREW, 1.0F, 1.2F);
		chat(t(ChatFormatting.GREEN, "satu_tungku.jadi", hasil.getHoverName(),
				mutu == null ? t("satu_tungku.tanpa_mutu") : mutu.displayName()));
		akhiri(nilai, "tuntas", false);
	}

	@Override
	public void waktuHabis() {
		akhiri(skor, "waktu", false);
	}

	@Override
	public int isiData(byte[] d) {
		d[1] = n(selesai() ? 2 : tungku != null && pemain.blockPosition().closerThan(tungku, 3.5D) ? 1 : 0);
		d[2] = n(coba);
		d[3] = n(mutuTerbaik);
		d[4] = n(tungku != null ? 1 : 0);
		return 4;
	}
}
