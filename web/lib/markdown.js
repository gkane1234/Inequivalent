import { marked, Renderer } from "../vendor/marked.esm.js";

export function renderMarkdown(markdown) {
  const renderer = new Renderer();
  const renderLink = renderer.link;
  renderer.link = function link(token) {
    return renderLink.call(this, token).replace("<a ", '<a target="_blank" rel="noopener noreferrer" ');
  };
  marked.use({ renderer, gfm: true });
  return marked
    .parse(markdown)
    .replace(/\$\$([\s\S]+?)\$\$/g, (_, tex) => `<span class="md-math">${tex}</span>`);
}

export async function loadMarkdown(url, element, failureText) {
  try {
    const response = await fetch(url);
    if (!response.ok) throw new Error(`status ${response.status}`);
    element.innerHTML = renderMarkdown(await response.text());
  } catch {
    element.textContent = failureText;
  }
}
