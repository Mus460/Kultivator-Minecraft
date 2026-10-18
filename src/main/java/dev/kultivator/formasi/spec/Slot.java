package dev.kultivator.formasi.spec;

/** One cared cell of a pattern relative to the eye, already rotated (DESIGN 24.2.2). */
public record Slot(int dx, int dy, int dz, char key, Rule rule) {
}
