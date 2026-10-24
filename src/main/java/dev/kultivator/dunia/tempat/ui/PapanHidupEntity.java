package dev.kultivator.dunia.tempat.ui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * {@code kultivator:papan_hidup} (DESIGN 38.O.2, 38.U.4): the living notice board of a town: rumours ({@link #JENIS_KABAR}),
 * wanted posters ({@link #JENIS_BURONAN}) and guild mission slips ({@link #JENIS_MISI}). An entity, never a block (38.0
 * rule 1); never saved, re-made by its anchor through {@link PapanHidup#pasang} (idempotent), invulnerable, not pushable.
 *
 * <p>Up to six sheets are synced, each a {@link Component} whose first sibling is the title and second the body line
 * (written with {@link #setLembar}); the client draws them with world text (38UI {@code TeksDunia}) on the 38MD board
 * model, the newest sheet "pasted" over 6 ticks. A wanted poster carries the wanted name (the client draws that player's
 * face when known) and the bounty. Using a mission board opens the guild board screen of whoever registered it
 * ({@code TempatLayar.bukaDari(.., gilda_misi, boardId)}; the builder decides whether this board serves it).
 */
public class PapanHidupEntity extends Entity {

	public static final String JENIS_KABAR = "kabar";
	public static final String JENIS_BURONAN = "buronan";
	public static final String JENIS_MISI = "misi";
	public static final int MAKS_LEMBAR = 6;

	private static final EntityDataAccessor<String> DATA_JENIS = SynchedEntityData.defineId(PapanHidupEntity.class, EntityDataSerializers.STRING);
	private static final EntityDataAccessor<Integer> DATA_JUMLAH = SynchedEntityData.defineId(PapanHidupEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_BERUBAH = SynchedEntityData.defineId(PapanHidupEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<String> DATA_WAJAH = SynchedEntityData.defineId(PapanHidupEntity.class, EntityDataSerializers.STRING);
	private static final EntityDataAccessor<Long> DATA_HADIAH = SynchedEntityData.defineId(PapanHidupEntity.class, EntityDataSerializers.LONG);
	@SuppressWarnings("unchecked")
	private static final EntityDataAccessor<Component>[] DATA_LEMBAR = new EntityDataAccessor[MAKS_LEMBAR];

	static {
		for (int i = 0; i < MAKS_LEMBAR; i++) {
			DATA_LEMBAR[i] = SynchedEntityData.defineId(PapanHidupEntity.class, EntityDataSerializers.COMPONENT);
		}
	}

	public PapanHidupEntity(EntityType<? extends PapanHidupEntity> type, Level level) {
		super(type, level);
		this.setNoGravity(true);
		this.setPermanentlyInvulnerable(true);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_JENIS, JENIS_KABAR);
		builder.define(DATA_JUMLAH, 0);
		builder.define(DATA_BERUBAH, -1000);
		builder.define(DATA_WAJAH, "");
		builder.define(DATA_HADIAH, 0L);
		for (EntityDataAccessor<Component> slot : DATA_LEMBAR) {
			builder.define(slot, Component.empty());
		}
	}

	// ------------------------------------------------------------------ server API

	public void setJenis(String jenis) {
		entityData.set(DATA_JENIS, jenis == null ? JENIS_KABAR : jenis);
	}

	/**
	 * Replaces the sheets (at most {@link #MAKS_LEMBAR}); {@code judul.get(i)} the title, {@code baris.get(i)} the body
	 * line (may be empty). Marks the board changed so the newest sheet is pasted on clients.
	 */
	public void setLembar(List<Component> judul, List<Component> baris) {
		int n = Math.min(MAKS_LEMBAR, judul.size());
		for (int i = 0; i < MAKS_LEMBAR; i++) {
			if (i < n) {
				Component b = i < baris.size() && baris.get(i) != null ? baris.get(i) : Component.empty();
				entityData.set(DATA_LEMBAR[i], Component.empty().append(judul.get(i)).append(b));
			} else {
				entityData.set(DATA_LEMBAR[i], Component.empty());
			}
		}
		entityData.set(DATA_JUMLAH, n);
		entityData.set(DATA_BERUBAH, tickCount);
	}

	/** Wanted poster: the wanted player's name (face) and the bounty in NB. */
	public void setBuronan(String nama, long hadiah) {
		entityData.set(DATA_WAJAH, nama == null ? "" : nama);
		entityData.set(DATA_HADIAH, Math.max(0L, hadiah));
	}

	// ------------------------------------------------------------------ both sides

	public String jenis() {
		return entityData.get(DATA_JENIS);
	}

	public int jumlahLembar() {
		return Math.max(0, Math.min(MAKS_LEMBAR, entityData.get(DATA_JUMLAH)));
	}

	/** Ticks since the sheets last changed. */
	public int sejakBerubah() {
		return Math.max(0, tickCount - entityData.get(DATA_BERUBAH));
	}

	public String wajah() {
		return entityData.get(DATA_WAJAH);
	}

	public long hadiah() {
		return entityData.get(DATA_HADIAH);
	}

	/** Title of sheet {@code i}. */
	public Component judul(int i) {
		List<Component> parts = bagian(i);
		return parts.isEmpty() ? Component.empty() : parts.get(0);
	}

	/** Body line of sheet {@code i}. */
	public Component baris(int i) {
		List<Component> parts = bagian(i);
		return parts.size() < 2 ? Component.empty() : parts.get(1);
	}

	private List<Component> bagian(int i) {
		if (i < 0 || i >= MAKS_LEMBAR) {
			return List.of();
		}
		Component c = entityData.get(DATA_LEMBAR[i]);
		return new ArrayList<>(c.getSiblings());
	}

	// ------------------------------------------------------------------ entity

	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		if (player instanceof ServerPlayer serverPlayer && JENIS_MISI.equals(jenis())) {
			return TempatLayar.bukaDari(serverPlayer, LembarKunci.GildaMisi.LAYAR, getId()) ? InteractionResult.SUCCESS : InteractionResult.PASS;
		}
		return player.level().isClientSide() && JENIS_MISI.equals(jenis()) ? InteractionResult.SUCCESS : InteractionResult.PASS;
	}

	@Override
	public boolean isPickable() {
		return true;
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public PushReaction getPistonPushReaction() {
		return PushReaction.IGNORE_ENTITY;
	}
}
