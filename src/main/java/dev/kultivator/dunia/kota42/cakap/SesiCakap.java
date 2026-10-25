package dev.kultivator.dunia.kota42.cakap;

import dev.kultivator.dunia.kota42.warga.Warga;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * One live player-NPC conversation (DESIGN 42.5A.6): state machine step, patience, turn counter, topic chain and the
 * options currently offered. Server only, never saved (the conversation history per warga and player is 42K's
 * {@code KeadaanPercakapan} store); dropped when the dialog screen closes or the player walks away.
 * <p>
 * Frozen shape (C42.K1, 42-G0): 42B's {@code BicaraWarga} reads the getters to build the dialog sheet; only the 42K engine
 * (same package) mutates the fields.
 */
public final class SesiCakap {

	/** Conversation states (42.5A.6 diagram). */
	public enum Tahap {
		PEMBUKA, BASA_BASI, TOPIK, LANJUTAN, PERMINTAAN, REAKSI, KILAS_BALIK, POTONG, PENUTUP
	}

	/**
	 * One player choice shown on the dialog sheet (intent; the tone is picked separately).
	 *
	 * @param niat    intent
	 * @param topik   topic id of {@code percakapan/topik.json}, null for topic-less intents (PAMIT, TOLAK ...)
	 * @param label   option text (lang key {@code kultivator.cakap.opsi.<niat>[.<topik>]} realised by 42K)
	 * @param terkunci shown but not selectable (missing requirement, e.g. MENYUAP without NB)
	 */
	public record Opsi(Niat niat, @Nullable String topik, Component label, boolean terkunci) {
	}

	final UUID pemain;
	final int npcId;
	final Warga warga;
	final long hariMulai;
	Tahap tahap = Tahap.PEMBUKA;
	int kesabaran;
	int giliran;
	@Nullable String topik;
	final List<String> topikRantai = new ArrayList<>();
	final List<Opsi> opsi = new ArrayList<>();
	final Set<Nada> nadaBoleh = EnumSet.of(Nada.SOPAN);
	final List<Ujaran> terakhir = new ArrayList<>();

	/**
	 * @param pemain    player UUID
	 * @param npcId     entity id of the speaking body
	 * @param warga     the stored person behind the body
	 * @param hariMulai world day the session started
	 * @param kesabaran starting patience 0-100 (42.5A.6 formula)
	 */
	public SesiCakap(UUID pemain, int npcId, Warga warga, long hariMulai, int kesabaran) {
		this.pemain = pemain;
		this.npcId = npcId;
		this.warga = warga;
		this.hariMulai = hariMulai;
		this.kesabaran = Math.max(0, Math.min(100, kesabaran));
	}

	public UUID pemain() {
		return pemain;
	}

	public int npcId() {
		return npcId;
	}

	public Warga warga() {
		return warga;
	}

	public long hariMulai() {
		return hariMulai;
	}

	public Tahap tahap() {
		return tahap;
	}

	public int kesabaran() {
		return kesabaran;
	}

	public int giliran() {
		return giliran;
	}

	/** Current topic id, null before the first topic. */
	public @Nullable String topik() {
		return topik;
	}

	/** Topics touched this session, oldest first (feeds {@code sudahDibahas} and the follow-up chain). */
	public List<String> topikRantai() {
		return List.copyOf(topikRantai);
	}

	public List<Opsi> opsi() {
		return List.copyOf(opsi);
	}

	/** Tones whose requirement holds right now (42.5A.7 "Syarat"). */
	public Set<Nada> nadaBoleh() {
		return Set.copyOf(nadaBoleh);
	}

	/** The NPC's last turn (1-3 utterances). */
	public List<Ujaran> terakhir() {
		return List.copyOf(terakhir);
	}

	public boolean selesai() {
		return tahap == Tahap.PENUTUP;
	}
}
