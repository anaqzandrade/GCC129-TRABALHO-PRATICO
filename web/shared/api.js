// Cliente HTTP comum às duas páginas. Tudo passa pelo API Gateway, na mesma origem da página.
const Ravita = (() => {
  const KEY = "ravita-session-" + location.pathname.split("/").filter(Boolean).slice(-1)[0];

  function session() {
    try { return JSON.parse(sessionStorage.getItem(KEY)); } catch { return null; }
  }

  function saveSession(value) {
    try { value ? sessionStorage.setItem(KEY, JSON.stringify(value)) : sessionStorage.removeItem(KEY); } catch { /* sem storage */ }
  }

  async function request(method, path, body) {
    const headers = { "X-Correlation-ID": crypto.randomUUID() };
    const current = session();
    if (current?.token) headers["Authorization"] = "Bearer " + current.token;
    if (body !== undefined) headers["Content-Type"] = "application/json";
    const response = await fetch(path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
    const text = await response.text();
    let data = null;
    try { data = text ? JSON.parse(text) : null; } catch { data = { message: text }; }
    if (!response.ok) {
      const error = new Error(data?.message || response.statusText);
      error.status = response.status;
      error.code = data?.code || String(response.status);
      error.correlationId = data?.correlationId || headers["X-Correlation-ID"];
      if (response.status === 401) saveSession(null);
      throw error;
    }
    return data;
  }

  async function login(role, organizationId) {
    const body = { role };
    if (organizationId) body.organizationId = organizationId;
    const data = await request("POST", "/auth/token", body);
    saveSession({ token: data.accessToken, role: data.role, organizationId: data.organizationId });
    return session();
  }

  function describeError(error) {
    return `${error.code}: ${error.message}` + (error.correlationId ? `\nCorrelation ID: ${error.correlationId}` : "");
  }

  function show(element, text, kind = "ok") {
    element.textContent = text;
    element.className = "msg " + kind;
    element.classList.toggle("hidden", !text);
  }

  function esc(value) {
    return String(value ?? "").replace(/[&<>"']/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
  }

  function when(value) {
    if (!value) return "";
    const date = new Date(value);
    return isNaN(date) ? esc(value) : date.toLocaleString("pt-BR", { dateStyle: "short", timeStyle: "short" });
  }

  const CATEGORIES = { VEGETABLES: "Hortaliças", FRUITS: "Frutas", GRAINS: "Grãos e secos", BAKERY: "Padaria",
    DAIRY: "Laticínios", PREPARED_FOOD: "Refeições prontas", OTHER: "Outros" };

  return { request, login, session, saveSession, describeError, show, esc, when, CATEGORIES };
})();
