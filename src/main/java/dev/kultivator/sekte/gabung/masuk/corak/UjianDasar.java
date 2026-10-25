package dev.kultivator.sekte.gabung.masuk.corak;

import dev.kultivator.alchemy.PillQuality;
import dev.kultivator.dunia.DuniaBudget;
import dev.kultivator.dunia.kota.KotaEntities;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Realm;
import dev.kultivator.sekte.gabung.api.Corak;
import dev.kultivator.sekte.gabung.api.GabungApi;
import dev.kultivator.sekte.gabung.masuk.GabungEntities;
import dev.kultivator.util.Msg;
import dev.kultivator.util.Targeting;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One running corak test of DESIGN 41.5.4 stage 2 (module 41B-C): the common frame every one of the ten tests of
 * {@code masuk/corak/*} extends. {@code UjianCorak} owns the life cycle (start, one {@link #tik} per server tick, the
 * Fabric hooks, the HUD, the time limit, death, leaving the field, teleport, the end) and calls the hooks below; a test
 * only builds its field in {@link #siapkan}, plays in {@link #tik} and decides with {@link #akhiri}.
 * <p>
 * Rules every test keeps (41.0): no block is ever changed (everything on the field is an entity, a {@code KultivatorNpc}
 * body, an ordinary mob or an item); every spawned thing goes through {@link #muncul} so it counts under the
 * {@link GabungEntities#ANGGARAN} budget and is removed in {@link #bersihkan}; items handed out for the test carry the
 * {@link #TAG_ITEM} custom data and are taken back at the end (also at the next login after a crash).
 * <p>
 * Server side only.
 */
public abstract class UjianDasar {

	/** Custom-data key of every item handed out by a corak test (value: the test kind). */
	public static final String TAG_ITEM = "kultivator_ujian_corak";
	/** Default time limit of a corak test (41.5.4: 180 s unless the test says otherwise). */
	public static final int BATAS_BAWAAN = 180 * 20;
	/** Default radius of the field; farther than this for {@link #KELUAR_TICK} ticks = left the field. */
	public static final double JARI_BAWAAN = 40.0D;
	public static final int KELUAR_TICK = 60;
	/** A titik farther than this from the player is ignored (planned gate column, a compound not yet generated). */
	public static final double TITIK_JAUH = 96.0D;

	protected ServerPlayer pemain;
	protected final ServerLevel level;
	protected final String sekte;
	protected final Corak corak;
	/** Class number 1-5 of the sect (41.4.1): the strength of opponents and the narrowness of bands. */
	protected final int kelas;
	/** Centre of the field (the test's main titik, else the player's feet). */
	protected final Vec3 pusat;
	protected final long benih;
	/** Ticks since the start. */
	protected int umur;
	/** The live stage score S2, 0-1 (shown on the HUD). */
	protected double skor;

	private final List<Entity> milik = new ArrayList<>();
	private boolean selesai;
	private double skorAkhir;
	private String alasan = "";
	private boolean gagal;

	protected UjianDasar(ServerPlayer pemain, String sekte, Corak corak, int kelas, Vec3 pusat, long benih) {
		this.pemain = pemain;
		this.level = pemain.level();
		this.sekte = sekte == null ? "" : sekte;
		this.corak = corak;
		this.kelas = Mth.clamp(kelas, 1, 5);
		this.pusat = pusat;
		this.benih = benih;
	}

	// ================================================================== the contract with UjianCorak

	/** Builds the field (entities, items, opening lines). Returns null when ready, else the reason it cannot start. */
	public abstract @Nullable Component siapkan();

	/** One server tick of the test (after the common checks). */
	public abstract void tik();

	/** Fills {@code data[1..]} of the HUD payload (data[0] is the corak ordinal, set by the caller); returns bytes used. */
	public abstract int isiData(byte[] data);

	/** The time limit in ticks. */
	public int batasTick() {
		return BATAS_BAWAAN;
	}

	/** The field radius around {@link #pusatWilayah()}. */
	public double jariWilayah() {
		return JARI_BAWAAN;
	}

	/** Centre of the field check (a long course like Pikul Gunung uses its midpoint). */
	public Vec3 pusatWilayah() {
		return pusat;
	}

	/** HUD rate in ticks (a needle game sends faster). */
	public int jedaHud() {
		return 5;
	}

	/** Time is up: the score as it stands (a test may override to judge differently). */
	public void waktuHabis() {
		akhiri(skor, "waktu", false);
	}

	/** The player would have died: the stage is failed (41.5.4 "mati = tahap gagal"). */
	public void tumbang() {
		akhiri(0.0D, "tumbang", true);
	}

	/** Fails the stage for a reason of the runner ({@code wilayah}, {@code teleport}, {@code keluar}, {@code batal}...). */
	public final void gagalkan(String sebab) {
		akhiri(0.0D, sebab, true);
	}

	// ---------------------------------------------------------------- hooks (default: nothing)

	/** ALLOW_DAMAGE of any living entity while this test runs; false cancels the blow. */
	public boolean bolehLuka(LivingEntity korban, DamageSource sumber, float jumlah) {
		return true;
	}

	/** AFTER_DEATH of any living entity while this test runs. */
	public void mati(LivingEntity korban, DamageSource sumber) {
	}

	/** The player used the item in {@code hand} (UseItemCallback); anything but PASS stops the vanilla use. */
	public InteractionResult pakaiItem(ItemStack stack, InteractionHand hand) {
		return InteractionResult.PASS;
	}

	/** The player right-clicked {@code entity} (UseEntityCallback; test entities with their own hook do not come here). */
	public InteractionResult pakaiEntitas(Entity entity, InteractionHand hand) {
		return InteractionResult.PASS;
	}

	/** A refining of this player at a furnace ended (AlchemyFurnace finish listener). */
	public void pilSelesai(ServerLevel tempat, BlockPos pos, ItemStack hasil, @Nullable PillQuality mutu, boolean sukses) {
	}

	// ================================================================== end

	/** Ends the test with stage score {@code nilai} (clamped 0-1); {@code sebab} names the lang line of the end. */
	protected final void akhiri(double nilai, String sebab, boolean gagal) {
		if (selesai) {
			return;
		}
		selesai = true;
		this.skorAkhir = Mth.clamp(nilai, 0.0D, 1.0D);
		this.alasan = sebab == null ? "" : sebab;
		this.gagal = gagal;
	}

	public final boolean selesai() {
		return selesai;
	}

	public final double skorAkhir() {
		return skorAkhir;
	}

	public final String alasan() {
		return alasan;
	}

	public final boolean gagal() {
		return gagal;
	}

	public final double skor() {
		return skor;
	}

	public final int umur() {
		return umur;
	}

	public final void majukan() {
		umur++;
	}

	public final Corak corak() {
		return corak;
	}

	public final String sekte() {
		return sekte;
	}

	public final ServerPlayer pemain() {
		return pemain;
	}

	public final void setPemain(ServerPlayer pemain) {
		this.pemain = pemain;
	}

	public final ServerLevel level() {
		return level;
	}

	/** True for an entity this test spawned. */
	public final boolean punya(Entity entity) {
		return milik.contains(entity);
	}

	/** Removes everything the test spawned and takes its items back (always called once, also on cancel). */
	public void bersihkan() {
		bersihkanKecuali(null);
	}

	/** {@link #bersihkan} that leaves entities of class {@code kecuali} standing (they leave by themselves, e.g. a fade). */
	protected final void bersihkanKecuali(@Nullable Class<?> kecuali) {
		for (Entity e : milik) {
			if (!e.isRemoved() && (kecuali == null || !kecuali.isInstance(e))) {
				e.discard();
			}
		}
		milik.clear();
		ambilItemUjian(pemain);
		for (ItemEntity dropped : level.getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(pusat, pusat).inflate(jariWilayah()),
				item -> itemUjian(item.getItem()))) {
			dropped.discard();
		}
	}

	// ================================================================== helpers for the tests

	/** Adds a spawned entity to the field (budget check first): true when it was added. */
	protected final boolean muncul(Entity entity) {
		if (entity == null || !DuniaBudget.mayAdd(level, GabungEntities.ANGGARAN) && !(entity instanceof dev.kultivator.sekte.gabung.masuk.DindingIlusiEntity)) {
			return false;
		}
		if (!level.addFreshEntity(entity)) {
			return false;
		}
		milik.add(entity);
		if (!GabungEntities.milik41(entity)) {
			// 41 types are tracked by GabungEntities' load hook; bodies and mobs are counted here (41.12.3).
			DuniaBudget.track(GabungEntities.ANGGARAN, entity);
		}
		return true;
	}

	/** Takes an entity another system already added (a rogue band) onto the field: counted and removed at the end. */
	protected final void ikut(Entity entity) {
		if (entity != null && !milik.contains(entity)) {
			milik.add(entity);
			DuniaBudget.track(GabungEntities.ANGGARAN, entity);
		}
	}

	/** Free budget slots for this test's spawns right now. */
	protected final int sisaAnggaran() {
		return Math.max(0, DuniaBudget.cap(GabungEntities.ANGGARAN) - DuniaBudget.count(GabungEntities.ANGGARAN));
	}

	/**
	 * A sect body for the test ({@code KultivatorNpc}, not saved): bronze monk, guard, outer disciple. Realm by class,
	 * tracked under the 41 budget. Null when the budget is full.
	 */
	protected final @Nullable KultivatorNpc tubuh(NpcRole peran, Vec3 di, float yaw, int realm, Component plat, @Nullable String rupa) {
		if (!DuniaBudget.mayAdd(level, GabungEntities.ANGGARAN)) {
			return null;
		}
		KultivatorNpc npc = KotaEntities.NPC.create(level, EntitySpawnReason.EVENT);
		if (npc == null) {
			return null;
		}
		Vec3 spot = Targeting.safeSpot(level, npc, di);
		if (spot == null) {
			spot = di;
		}
		int look = (int) (benih ^ (benih >>> 32)) ^ (int) BlockPos.containing(spot).asLong() ^ (peran.ordinal() * 0x9E3779B9);
		npc.snapTo(spot.x, spot.y, spot.z, yaw, 0.0F);
		npc.setup(peran, Mth.clamp(kelas - 1, 0, 4), look, realm, Math.floorMod(look >>> 5, 4), Dao.values()[Math.floorMod(look >>> 11, Dao.COUNT)],
				Math.floorMod(look >>> 3, 6), 0, plat);
		npc.setHome(BlockPos.containing(spot));
		npc.setLonely(80.0D, 1200);
		npc.memory().putBoolean("gabung_ujian", true);
		if (rupa != null) {
			dev.kultivator.sosok.SosokApi.setRupa(npc, rupa);
		}
		if (!muncul(npc)) {
			return null;
		}
		return npc;
	}

	/** Realm ordinal of a test opponent of this class (Kecil: Pemurnian Qi .. Abadi: Jiwa Baru). */
	protected final int realmLawan() {
		return switch (kelas) {
			case 1 -> Realm.QI_REFINING.ordinal();
			case 2 -> Realm.FOUNDATION.ordinal();
			case 3 -> Realm.GOLDEN_CORE.ordinal();
			case 4 -> Realm.NASCENT_SOUL.ordinal();
			default -> Realm.DEITY_TRANSFORMATION.ordinal();
		};
	}

	/**
	 * A titik of the sect when it is near and sane (within {@link #TITIK_JAUH} of the player and 24 blocks of its height:
	 * a planned gate column has y 0), else null.
	 */
	protected final @Nullable Vec3 titik(String nama) {
		if (sekte.isEmpty()) {
			return null;
		}
		BlockPos pos = GabungApi.titik(sekte, nama);
		if (pos == null) {
			return null;
		}
		Vec3 v = Vec3.atBottomCenterOf(pos);
		if (v.distanceTo(pemain.position()) > TITIK_JAUH || Math.abs(v.y - pemain.getY()) > 24.0D) {
			return null;
		}
		return v;
	}

	/** True when two titik resolved to the same spot (a stand-in answered for both). */
	protected static boolean sama(@Nullable Vec3 a, @Nullable Vec3 b) {
		return a != null && b != null && a.distanceToSqr(b) < 1.0D;
	}

	/**
	 * The standing spot of column ({@code x}, {@code z}) near height {@code y}: the first block from {@code y + 4} down to
	 * {@code y - 8} with a sturdy top face and two free blocks above. Only reads blocks.
	 */
	protected final Vec3 tanah(double x, double y, double z) {
		int bx = Mth.floor(x);
		int bz = Mth.floor(z);
		int top = Mth.floor(y) + 4;
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int by = top; by >= top - 12; by--) {
			m.set(bx, by - 1, bz);
			BlockState below = level.getBlockState(m);
			if (!below.isFaceSturdy(level, m, Direction.UP)) {
				continue;
			}
			m.set(bx, by, bz);
			if (!level.getBlockState(m).getCollisionShape(level, m).isEmpty() || !level.getFluidState(m).isEmpty()) {
				continue;
			}
			m.set(bx, by + 1, bz);
			if (!level.getBlockState(m).getCollisionShape(level, m).isEmpty()) {
				continue;
			}
			return new Vec3(x, by, z);
		}
		return new Vec3(x, Math.floor(y), z);
	}

	/** {@link #tanah} at an offset from {@link #pusat}. */
	protected final Vec3 dariPusat(double dx, double dz) {
		return tanah(pusat.x + dx, pusat.y, pusat.z + dz);
	}

	/** Yaw (degrees, Minecraft convention) looking from {@code a} to {@code b}. */
	protected static float yaw(Vec3 a, Vec3 b) {
		return (float) (Mth.atan2(b.z - a.z, b.x - a.x) * Mth.RAD_TO_DEG) - 90.0F;
	}

	/** Marks {@code stack} as a test item of kind {@code jenis}, with a name and lore lines; returns it. */
	protected static ItemStack itemUjian(ItemStack stack, String jenis, @Nullable Component nama, Component... lore) {
		CompoundTag tag = new CompoundTag();
		tag.putString(TAG_ITEM, jenis);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		if (nama != null) {
			stack.set(DataComponents.CUSTOM_NAME, nama);
		}
		if (lore.length > 0) {
			stack.set(DataComponents.LORE, new ItemLore(List.of(lore)));
		}
		return stack;
	}

	/** True for an item handed out by a corak test. */
	public static boolean itemUjian(ItemStack stack) {
		return !jenisItem(stack).isEmpty();
	}

	/** The test kind of a test item ("" for any other stack). */
	public static String jenisItem(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return "";
		}
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? "" : data.copyTag().getStringOr(TAG_ITEM, "");
	}

	/** Gives a test item (dropped at the feet when the inventory is full). */
	protected final void beri(ItemStack stack) {
		if (!pemain.addItem(stack) && !stack.isEmpty()) {
			level.addFreshEntity(new ItemEntity(level, pemain.getX(), pemain.getY() + 0.5D, pemain.getZ(), stack));
		}
	}

	/** Removes every corak-test item from the player's inventory. */
	public static void ambilItemUjian(@Nullable ServerPlayer player) {
		if (player == null) {
			return;
		}
		var inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (itemUjian(inv.getItem(i))) {
				inv.setItem(i, ItemStack.EMPTY);
			}
		}
		if (itemUjian(player.containerMenu.getCarried())) {
			player.containerMenu.setCarried(ItemStack.EMPTY);
		}
	}

	/** Counts test items of kind {@code jenis} in the player's inventory. */
	protected final int hitungItem(String jenis) {
		int n = 0;
		var inv = pemain.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (jenis.equals(jenisItem(s))) {
				n += s.getCount();
			}
		}
		return n;
	}

	/** A sound at {@code at} for everyone near (players' own category). */
	protected final void bunyi(Vec3 at, net.minecraft.sounds.SoundEvent suara, float volume, float pitch) {
		level.playSound(null, at.x, at.y, at.z, suara, net.minecraft.sounds.SoundSource.PLAYERS, volume, pitch);
	}

	/** A line on the action bar. */
	protected final void bar(Component text) {
		Msg.actionBar(pemain, text);
	}

	/** A line in chat (prefixed). */
	protected final void chat(Component text) {
		Msg.chat(pemain, text);
	}

	/** {@code kultivator.gabung.corak.<key>} with arguments. */
	protected static MutableComponent t(String key, Object... args) {
		return Component.translatable("kultivator.gabung.corak." + key, args);
	}

	/** The same in a colour. */
	protected static MutableComponent t(ChatFormatting warna, String key, Object... args) {
		return t(key, args).withStyle(warna);
	}

	/** Seconds left of the limit (rounded up). */
	protected final int sisaDetik() {
		return Math.max(0, (batasTick() - umur + 19) / 20);
	}

	/** True when {@code e} was hurt by this test's player (directly or by the player's projectile). */
	protected final boolean olehPemain(DamageSource sumber) {
		Entity e = sumber.getEntity();
		return e instanceof ServerPlayer p && p.getUUID().equals(pemain.getUUID());
	}

	/** A small deterministic random of this test (seeded per player, sect and day). */
	protected final java.util.Random acak() {
		return new java.util.Random(benih * 31L + umur);
	}

	protected final UUID uuid() {
		return pemain.getUUID();
	}

	/** Byte of a 0-1 value. */
	protected static byte b(double v) {
		return (byte) Mth.clamp((int) Math.round(v * 255.0D), 0, 255);
	}

	/** Byte of a small integer (0-255). */
	protected static byte n(int v) {
		return (byte) Mth.clamp(v, 0, 255);
	}
}
