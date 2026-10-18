package dev.kultivator.formasi.spec;

/** The entry of the spec DSL (DESIGN 24.2.2): {@code r.add(Specs.spec("small_spirit_gathering", 110, Family.PENGUMPUL)...build())}. */
public final class Specs {

	public static Builder spec(String id, int netId, Family family) {
		return new Builder(id, netId, family);
	}

	private Specs() {
	}
}
