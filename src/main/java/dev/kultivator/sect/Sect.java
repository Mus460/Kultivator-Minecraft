package dev.kultivator.sect;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * One sect: its name, Sect Master (Ketua Sekte), members with their titles and join times, founding time and colour.
 * The master is also listed in {@link #members()} with the {@link #MASTER} title. Changed only through {@link SectData},
 * which marks the saved data dirty.
 */
public final class Sect {

	/** Member titles (saved as these ids). */
	public static final String MASTER = "master";
	public static final String DISCIPLE = "disciple";

	public static final int MIN_NAME = 3;
	public static final int MAX_NAME = 24;

	/** One member of a sect. {@code joined} is the overworld game time when they joined. */
	public record Member(UUID id, String name, long joined, String title) {
		public static final Codec<Member> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				UUIDUtil.CODEC.fieldOf("id").forGetter(Member::id),
				Codec.STRING.optionalFieldOf("name", "?").forGetter(Member::name),
				Codec.LONG.optionalFieldOf("joined", 0L).forGetter(Member::joined),
				Codec.STRING.optionalFieldOf("title", DISCIPLE).forGetter(Member::title)
		).apply(instance, Member::new));

		public boolean isMaster() {
			return MASTER.equals(title);
		}

		Member withTitle(String newTitle) {
			return new Member(id, name, joined, newTitle);
		}

		Member withName(String newName) {
			return new Member(id, newName, joined, title);
		}

		/** "Sect Master" / "Disciple", translated. */
		public Component titleName() {
			return Component.translatable("kultivator.sects.title." + (isMaster() ? MASTER : DISCIPLE));
		}
	}

	public static final Codec<Sect> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.fieldOf("name").forGetter(Sect::name),
			UUIDUtil.CODEC.fieldOf("master").forGetter(Sect::master),
			Codec.STRING.optionalFieldOf("master_name", "?").forGetter(Sect::masterName),
			Member.CODEC.listOf().optionalFieldOf("members", List.of()).forGetter(Sect::members),
			Codec.LONG.optionalFieldOf("created", 0L).forGetter(Sect::created),
			Codec.INT.optionalFieldOf("color", -1).forGetter(Sect::color)
	).apply(instance, Sect::new));

	private final String name;
	private UUID master;
	private String masterName;
	private final List<Member> members = new ArrayList<>();
	private final long created;
	private final int color;

	Sect(String name, UUID master, String masterName, List<Member> members, long created, int color) {
		this.name = normalizeName(name);
		this.master = master;
		this.masterName = masterName;
		this.created = created;
		this.color = color < 0 ? colorFor(this.name) : color & 0xFFFFFF;
		// Keeps the saved list consistent: one entry per player, the master listed once with the master title.
		boolean masterListed = false;
		for (Member member : members) {
			if (indexOf(member.id()) >= 0) {
				continue;
			}
			boolean isMaster = member.id().equals(master);
			masterListed |= isMaster;
			this.members.add(member.withTitle(isMaster ? MASTER : DISCIPLE));
		}
		if (!masterListed) {
			this.members.add(0, new Member(master, masterName, created, MASTER));
		}
		sortMembers();
	}

	/** A new sect founded right now by {@code founder}. */
	static Sect found(String name, UUID founder, String founderName, long now) {
		return new Sect(name, founder, founderName, List.of(new Member(founder, founderName, now, MASTER)), now, -1);
	}

	// ================================================================== reading

	public String name() {
		return name;
	}

	/** Case-insensitive unique key of this sect. */
	public String key() {
		return key(name);
	}

	public UUID master() {
		return master;
	}

	public String masterName() {
		return masterName;
	}

	/** Every member, the master included, oldest first. */
	public List<Member> members() {
		return List.copyOf(members);
	}

	public int size() {
		return members.size();
	}

	public long created() {
		return created;
	}

	/** RGB colour derived from the name. */
	public int color() {
		return color;
	}

	public boolean isMember(UUID id) {
		return indexOf(id) >= 0;
	}

	public boolean isMaster(UUID id) {
		return master.equals(id);
	}

	public @Nullable Member member(UUID id) {
		int index = indexOf(id);
		return index < 0 ? null : members.get(index);
	}

	/** The member called {@code playerName} (ignoring case), or null. */
	public @Nullable Member memberByName(String playerName) {
		for (Member member : members) {
			if (member.name().equalsIgnoreCase(playerName)) {
				return member;
			}
		}
		return null;
	}

	/** The coloured "[Name]" tag; clicking it shows the sect's info. */
	public MutableComponent tag() {
		return Component.literal("[" + name + "]").withStyle(style -> style.withColor(color)
				.withClickEvent(new ClickEvent.RunCommand("/sect info " + name))
				.withHoverEvent(new HoverEvent.ShowText(Component.translatable("kultivator.sects.list.hover", name))));
	}

	/** The sect name in its colour, bold. */
	public MutableComponent coloredName() {
		return Component.literal(name).withStyle(style -> style.withColor(color).withBold(true));
	}

	/** A member's name coloured by title (the master in gold). */
	public static MutableComponent memberName(Member member) {
		return Component.literal(member.name()).withStyle(member.isMaster() ? ChatFormatting.GOLD : ChatFormatting.WHITE);
	}

	// ================================================================== changes (through SectData)

	void add(UUID id, String playerName, long now) {
		if (indexOf(id) < 0) {
			members.add(new Member(id, playerName, now, DISCIPLE));
			sortMembers();
		}
	}

	/** Removes a member; the master is never removed this way (see {@link #passMaster}). */
	void remove(UUID id) {
		int index = indexOf(id);
		if (index >= 0 && !members.get(index).isMaster()) {
			members.remove(index);
		}
	}

	/**
	 * The master leaves: the oldest other member becomes Sect Master. Returns the new master, or null when the master was
	 * alone (the caller then disbands the sect).
	 */
	@Nullable Member passMaster() {
		Member heir = null;
		for (Member member : members) {
			if (!member.isMaster() && (heir == null || member.joined() < heir.joined())) {
				heir = member;
			}
		}
		if (heir == null) {
			return null;
		}
		int oldIndex = indexOf(master);
		if (oldIndex >= 0) {
			members.remove(oldIndex);
		}
		int heirIndex = indexOf(heir.id());
		Member promoted = heir.withTitle(MASTER);
		members.set(heirIndex, promoted);
		master = promoted.id();
		masterName = promoted.name();
		sortMembers();
		return promoted;
	}

	/** Remembers a renamed player's current name. True when something changed. */
	boolean rename(UUID id, String newName) {
		int index = indexOf(id);
		if (index < 0 || members.get(index).name().equals(newName)) {
			return false;
		}
		members.set(index, members.get(index).withName(newName));
		if (master.equals(id)) {
			masterName = newName;
		}
		return true;
	}

	private int indexOf(UUID id) {
		for (int i = 0; i < members.size(); i++) {
			if (members.get(i).id().equals(id)) {
				return i;
			}
		}
		return -1;
	}

	/** The master first, then everyone else by join time. */
	private void sortMembers() {
		members.sort(Comparator.comparing((Member member) -> !member.isMaster()).thenComparingLong(Member::joined));
	}

	// ================================================================== names

	/** Trims and collapses runs of spaces. */
	public static String normalizeName(String raw) {
		return raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
	}

	public static String key(String raw) {
		return normalizeName(raw).toLowerCase(Locale.ROOT);
	}

	/** 3 to 24 characters, only letters, digits, spaces, '-' and '_', and at least one letter or digit. */
	public static boolean validName(String raw) {
		String name = normalizeName(raw);
		if (name.length() < MIN_NAME || name.length() > MAX_NAME) {
			return false;
		}
		boolean letterOrDigit = false;
		for (int i = 0; i < name.length(); i++) {
			char c = name.charAt(i);
			if (Character.isLetterOrDigit(c)) {
				letterOrDigit = true;
			} else if (c != ' ' && c != '-' && c != '_') {
				return false;
			}
		}
		return letterOrDigit;
	}

	/** A bright colour picked from the name, so every sect keeps its own banner colour. */
	public static int colorFor(String name) {
		int hash = key(name).hashCode();
		float hue = ((hash & 0x7FFFFFFF) % 360) / 360.0F;
		float saturation = 0.55F + ((hash >>> 9) & 0xF) / 15.0F * 0.3F;
		return Mth.hsvToRgb(hue, saturation, 1.0F) & 0xFFFFFF;
	}
}
