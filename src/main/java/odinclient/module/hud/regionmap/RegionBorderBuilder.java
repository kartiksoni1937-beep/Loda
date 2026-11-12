package odinclient.module.hud.regionmap;

import odinclient.module.world.ChunkAreaMerger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

public final class RegionBorderBuilder {
    public static final float LINE_THICKNESS = 1.6F;
    public static final int MAX_ALPHA = 245;
    public static final double LINE_HEIGHT_OFFSET = 0.026;

    private RegionBorderBuilder() {}

    public static RegionBorderBuilder.BorderResult build(
            List<ChunkAreaMerger.ChunkArea> areas, int chunkX, int chunkZ, int radius) {
        if (areas.isEmpty()) {
            return RegionBorderBuilder.BorderResult.EMPTY;
        } else {
            List<ChunkAreaMerger.ChunkArea> clippedAreas = new ArrayList<>();
            Map<Long, List<RegionBorderBuilder.Span>> columnSpans = new TreeMap<>();
            Map<Long, List<RegionBorderBuilder.Span>> rowSpans = new TreeMap<>();

            for (ChunkAreaMerger.ChunkArea area : areas) {
                ChunkAreaMerger.ChunkArea clipped = area.clip(chunkX, chunkZ, radius);
                if (clipped != null) {
                    clippedAreas.add(clipped);

                    for (long x = clipped.minimumX(); x <= clipped.maximumXExclusive(); x++) {
                        columnSpans
                                .computeIfAbsent(x, key -> new ArrayList<>())
                                .add(
                                        new RegionBorderBuilder.Span(
                                                clipped.minimumZ(), clipped.maximumZExclusive()));
                    }

                    for (long z = clipped.minimumZ(); z <= clipped.maximumZExclusive(); z++) {
                        rowSpans.computeIfAbsent(z, key -> new ArrayList<>())
                                .add(
                                        new RegionBorderBuilder.Span(
                                                clipped.minimumX(), clipped.maximumXExclusive()));
                    }
                }
            }

            if (clippedAreas.isEmpty()) {
                return RegionBorderBuilder.BorderResult.EMPTY;
            } else {
                List<RegionBorderBuilder.BorderLine> lines = new ArrayList<>();
                mergeSpans(lines, columnSpans, true);
                mergeSpans(lines, rowSpans, false);
                return new RegionBorderBuilder.BorderResult(
                        List.copyOf(clippedAreas), List.copyOf(lines));
            }
        }
    }

    private static void mergeSpans(
            List<RegionBorderBuilder.BorderLine> lines,
            Map<Long, List<RegionBorderBuilder.Span>> spansByLine,
            boolean vertical) {
        for (Entry<Long, List<RegionBorderBuilder.Span>> entry : spansByLine.entrySet()) {
            List<RegionBorderBuilder.Span> spans = entry.getValue();
            spans.sort(
                    Comparator.comparingLong(RegionBorderBuilder.Span::start)
                            .thenComparingLong(RegionBorderBuilder.Span::end));
            long from = spans.getFirst().start;
            long to = spans.getFirst().end;

            for (int i = 1; i < spans.size(); i++) {
                RegionBorderBuilder.Span span = spans.get(i);
                if (span.start <= to) {
                    to = Math.max(to, span.end);
                } else {
                    addLine(lines, entry.getKey(), from, to, vertical);
                    from = span.start;
                    to = span.end;
                }
            }

            addLine(lines, entry.getKey(), from, to, vertical);
        }
    }

    private static void addLine(
            List<RegionBorderBuilder.BorderLine> lines,
            long fixed,
            long start,
            long end,
            boolean vertical) {
        if (vertical) {
            lines.add(new RegionBorderBuilder.BorderLine(fixed, start, fixed, end));
        } else {
            lines.add(new RegionBorderBuilder.BorderLine(start, fixed, end, fixed));
        }
    }

    public record BorderLine(long x1, long z1, long x2, long z2) {
        public double startBlockX() {
            return this.x1 * 16.0;
        }

        public double startBlockZ() {
            return this.z1 * 16.0;
        }

        public double endBlockX() {
            return this.x2 * 16.0;
        }

        public double endBlockZ() {
            return this.z2 * 16.0;
        }
    }

    public record BorderResult(
            List<ChunkAreaMerger.ChunkArea> areas, List<RegionBorderBuilder.BorderLine> lines) {
        public static final RegionBorderBuilder.BorderResult EMPTY =
                new RegionBorderBuilder.BorderResult(List.of(), List.of());
    }

    private record Span(long start, long end) {}
}
