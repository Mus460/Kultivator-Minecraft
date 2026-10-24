package dev.kultivator.dunia.tempat.gua;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.reruntuhan.RRewards;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Panggung Bintang (DESIGN 38.C.5, 38.A #20 and #22, anchor {@code gua/bintang_panggung} at the centre of a round
 * quartz stage on a high meadow): a static ring of stone and the armillary sphere, drawn by the client as a turning
 * model ({@code HiasanDunia} ARMILARI, sent by {@link GuaPayload#hias}). A star calamity passed on the stage rolls the
 * reward table once more ({@code StarCalamity} finish hook, loot table {@code kultivator:gua/hadiah_bintang}); watching
 * the sky's events here counts as the capital's observatory: meteor stones ×2 at a great landing and Kristal Gerhana
 * from 35 % to 50 % (the daily cap unchanged), through the {@code TempatApi} factor {@code bintang}. The stage centre is
 * remembered as a treasure-birth place (38.A #23). The "wide camera" of 38.A #20 comes when the calamity was passed (the
 * danger is over; the 7b rule "never during real danger"): a short pull-back over the stage ({@link GuaPayload#kamera}).
 */
public final class PanggungBintang extends GuaRun {

	public static final ResourceKey<LootTable> HADIAH = ResourceKey.create(Registries.LOOT_TABLE, Kultivator.id("gua/hadiah_bintang"));

	private final Set<UUID> dikirim = new HashSet<>();

	public PanggungBintang(DuniaAnchorEntity anchor, ServerLevel level) {
		super(anchor, level);
	}

	@Override
	public double radius() {
		return Math.max(8, Math.min(14, data().getIntOr("r", 10))) + 2.0D;
	}

	private String grup() {
		return "gua/bintang/" + situs();
	}

	@Override
	protected void spawn() {
		dikirim.clear();
	}

	@Override
	protected void nearby(List<ServerPlayer> within) {
		for (ServerPlayer player : within) {
			if (dikirim.add(player.getUUID())) {
				ListTag isi = new ListTag();
				isi.add(GuaPayload.hiasan("ARMILARI", Vec3.atBottomCenterOf(anchor.blockPosition()), frame.yaw(0.0F), 0.0F));
				GuaPayload.hias(player, grup(), isi);
			}
		}
	}

	@Override
	protected void tick(long now) {
		if (now % 60L == 0L && !level.isBrightOutside()) {
			Vec3 c = centre();
			level.sendParticles(ParticleTypes.END_ROD, c.x, c.y + 3.0D, c.z, 3, 2.0D, 1.0D, 2.0D, 0.005D);
		}
	}

	/** A star calamity ended for {@code player}; when passed on a stage, the reward table rolls once more. */
	static void bencana(ServerPlayer player, int k, boolean lulus) {
		if (!lulus || !GuaConfig.on()) {
			return;
		}
		PanggungBintang run = GuaRuns.at(player, PanggungBintang.class);
		if (run == null) {
			return;
		}
		ServerLevel level = player.level();
		LootTable table = level.getServer().reloadableRegistries().getLootTable(HADIAH);
		LootParams params = new LootParams.Builder(level)
				.withParameter(LootContextParams.ORIGIN, player.position())
				.withOptionalParameter(LootContextParams.THIS_ENTITY, player)
				.create(LootContextParamSets.CHEST);
		List<ItemStack> items = new ArrayList<>(table.getRandomItems(params));
		if (items.isEmpty()) {
			return;
		}
		RRewards.giveAll(player, items, "kultivator.gua.bintang.gulir");
		Vec3 c = run.centre();
		level.sendParticles(ParticleTypes.END_ROD, c.x, c.y + 2.0D, c.z, 80, 3.0D, 2.0D, 3.0D, 0.1D);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.4F, 1.2F);
		GuaPayload.kamera(player, "gua/bintang", c.add(0.0D, 1.0D, 0.0D), "kultivator.gua.bintang.kartu", "星", 0xA8F0FF);
		Msg.chat(player, Component.translatable("kultivator.gua.bintang.gulir.chat", k).withStyle(ChatFormatting.AQUA));
	}

	@Override
	protected void despawn() {
		GuaPayload.hapusHias(level, centre(), grup());
		dikirim.clear();
		super.despawn();
	}
}
