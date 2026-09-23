// 139 -> "2h 19m"
export const formatRuntime = (minutes) => {
    if (!minutes) return null;
    const h = Math.floor(minutes / 60);
    const m = minutes % 60;
    return h > 0 ? `${h}h ${m}m` : `${m}m`;
};

// "2022 · 2h 19m · ★ 7.4" (skips anything that's missing)
export const formatMeta = (movie) => {
    if (!movie) return "";
    return [
        movie.releaseDate?.substring(0, 4),
        formatRuntime(movie.runtime),
        movie.rating ? `★ ${movie.rating.toFixed(1)}` : null,
    ].filter(Boolean).join(" · ");
};
