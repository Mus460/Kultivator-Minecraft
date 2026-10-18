package dev.kultivator.formasi.spec;

/** Where catalogues register their specs (DESIGN 24.2.2); a refused spec is logged, never thrown. */
@FunctionalInterface
public interface SpecSink {
	void add(FormationSpec spec);
}
