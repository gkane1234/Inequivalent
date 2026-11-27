/**
 * The 24-game card. Four numbers sit upright, so the card reads without turning.
 * Corner dots mark the band: one white, two red, three yellow, four green.
 */

const SIZE = 400;
const CENTER = 200;
const CIRCLE_R = 170;
const SQUARE = 84;
const FAN_INNER = 48;
const FAN_OUTER = 156;
const FAN_HALF = 18;
const FAN_LINES = 7;
const LINE_WIDTH = 4.4;
const DOT_R = 7.5;
const NUMBER_OFFSET = 106;
const NUMBER_SIZE = 76;

const BLUE = "#1e4ed8";
const GOLD = "#f5c400";
const PINK = "#ff3a8c";
const INK = "#141414";

/** @type {Record<string, { dots: number, color: string, label: string }>} */
export const WHEEL_BANDS = {
  easy: { dots: 1, color: "#ffffff", label: "Easy" },
  medium: { dots: 2, color: "#ef1e2a", label: "Medium" },
  hard: { dots: 3, color: "#ffe14a", label: "Hard" },
  hardest: { dots: 4, color: "#3dde55", label: "Hardest" },
};

/** Local spots in the top-left corner. The other corners mirror these. */
const DOT_LAYOUT = {
  1: [[24, 24]],
  2: [[22, 14], [22, 38]],
  3: [[14, 42], [26, 26], [42, 14]],
  4: [[16, 16], [16, 38], [38, 16], [38, 38]],
};

const FAN_ANGLES = [-135, -45, 45, 135];

/**
 * @param {number[]} values four numbers, top then clockwise
 * @param {string} band easy, medium, hard, or hardest
 * @param {string} [id] unique prefix when several wheels share a page
 */
export function wheelSvg(values, band, id = "wheel24") {
  const spec = WHEEL_BANDS[band];
  if (!spec) throw new Error(`Unknown difficulty ${band}`);
  if (!Array.isArray(values) || values.length !== 4) {
    throw new Error("A 24 wheel needs four numbers");
  }
  const safe = safeId(id);
  const digits = values.map(digitText);
  const label = `${spec.label} 24 card. Numbers ${digits.join(", ")}.`;
  const half = SQUARE / 2;
  const fans = FAN_ANGLES.map((angle, index) => fanMarkup(safe, angle, index)).join("");
  const [top, right, bottom, left] = digits.map(escapeXml);
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${SIZE} ${SIZE}" role="img" aria-label="${escapeXml(label)}">
<title>${escapeXml(label)}</title>
<rect width="${SIZE}" height="${SIZE}" fill="${BLUE}" />
<circle cx="${CENTER}" cy="${CENTER}" r="${CIRCLE_R}" fill="${GOLD}" />
<defs>
${FAN_ANGLES.map((angle, index) => `<clipPath id="${safe}-fan-${index}"><polygon points="${fanPoints(angle)}" /></clipPath>`).join("\n")}
</defs>
${fans}
<rect x="${CENTER - half}" y="${CENTER - half}" width="${SQUARE}" height="${SQUARE}" fill="#fff" />
<g class="wheel-nums" fill="${INK}" font-family="Lilita One, 'Arial Black', 'Noto Sans Display', sans-serif" font-size="${NUMBER_SIZE}" font-weight="700" text-anchor="middle" dominant-baseline="central">
<text class="wheel-num" x="${CENTER}" y="${CENTER - NUMBER_OFFSET}">${top}</text>
<text class="wheel-num" x="${CENTER + NUMBER_OFFSET}" y="${CENTER}">${right}</text>
<text class="wheel-num" x="${CENTER}" y="${CENTER + NUMBER_OFFSET}">${bottom}</text>
<text class="wheel-num" x="${CENTER - NUMBER_OFFSET}" y="${CENTER}">${left}</text>
</g>
${dotMarkup(spec.dots, spec.color)}
</svg>`;
}

function fanMarkup(id, angle, index) {
  const points = fanPoints(angle);
  const lines = [];
  for (let i = 1; i <= FAN_LINES; i++) {
    const deg = angle - FAN_HALF + (i / (FAN_LINES + 1)) * FAN_HALF * 2;
    const [x1, y1] = polar(deg, FAN_INNER);
    const [x2, y2] = polar(deg, FAN_OUTER);
    lines.push(
      `<line x1="${fmt(x1)}" y1="${fmt(y1)}" x2="${fmt(x2)}" y2="${fmt(y2)}" stroke="#fff" stroke-width="${LINE_WIDTH}" />`
    );
  }
  return `<g clip-path="url(#${id}-fan-${index})"><polygon points="${points}" fill="${PINK}" />${lines.join("")}</g>`;
}

function fanPoints(angle) {
  const pts = [
    polar(angle - FAN_HALF, FAN_INNER),
    polar(angle - FAN_HALF, FAN_OUTER),
    polar(angle + FAN_HALF, FAN_OUTER),
    polar(angle + FAN_HALF, FAN_INNER),
  ];
  return pts.map(([x, y]) => `${fmt(x)},${fmt(y)}`).join(" ");
}

function dotMarkup(count, color) {
  const spots = [];
  for (const [x, y] of DOT_LAYOUT[count]) {
    spots.push([x, y], [SIZE - x, y], [SIZE - x, SIZE - y], [x, SIZE - y]);
  }
  return spots
    .map(
      ([x, y]) =>
        `<circle class="wheel-dot" cx="${fmt(x)}" cy="${fmt(y)}" r="${DOT_R}" fill="${color}" />`
    )
    .join("");
}

function polar(deg, radius) {
  const rad = (deg * Math.PI) / 180;
  return [CENTER + Math.cos(rad) * radius, CENTER + Math.sin(rad) * radius];
}

function digitText(value) {
  if (!Number.isFinite(value)) throw new Error("Wheel numbers must be finite");
  return Number.isInteger(value) ? String(value) : String(value);
}

function safeId(id) {
  if (!/^[A-Za-z][\w-]*$/.test(id)) throw new Error("Invalid wheel id");
  return id;
}

function escapeXml(value) {
  return String(value).replace(/[&<>"']/g, (ch) => {
    if (ch === "&") return "&amp;";
    if (ch === "<") return "&lt;";
    if (ch === ">") return "&gt;";
    if (ch === '"') return "&quot;";
    return "&apos;";
  });
}

function fmt(value) {
  return String(Math.round(value * 100) / 100);
}
