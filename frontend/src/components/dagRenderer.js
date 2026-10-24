/**
 * frontend/src/components/dagRenderer.js
 * Renders interactive 2D SVG Control-Flow Graphs (CFG) with explicit True/False diamonds,
 * Loop Back-Edge curves, and live execution trace badges.
 */
export function buildLoopBackSvgBadge(loopDepth) {
  return `<span class="px-1.5 py-0.5 rounded bg-indigo-500/20 text-indigo-300 text-[10px] font-mono">↻ Обратная дуга (Глубина=${loopDepth})</span>`;
}
