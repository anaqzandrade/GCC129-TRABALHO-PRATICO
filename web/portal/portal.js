// Portal de doadores e instituições: fala só com o Portal BFF (/portal/v1), pelo gateway.
const $ = id => document.getElementById(id);
const { request, login, session, saveSession, describeError, show, esc, when, CATEGORIES } = Ravita;

function fillCategories() {
  for (const [code, label] of Object.entries(CATEGORIES)) {
    $("filter-category").add(new Option(label, code));
    $("offer-category").add(new Option(label, code));
  }
  const until = new Date(Date.now() + 6 * 3600 * 1000);
  until.setMinutes(until.getMinutes() - until.getTimezoneOffset());
  document.querySelector("[name=availableUntil]").value = until.toISOString().slice(0, 16);
}

function render() {
  const current = session();
  $("login-section").classList.toggle("hidden", !!current);
  $("app").classList.toggle("hidden", !current);
  $("logout").classList.toggle("hidden", !current);
  if (!current) { $("who").textContent = ""; return; }
  const donor = current.role === "DONOR";
  $("who").textContent = `${donor ? "Doador" : "Instituição"} · ${current.organizationId}`;
  document.querySelectorAll(".donor-only").forEach(el => el.classList.toggle("hidden", !donor));
  document.querySelectorAll(".institution-only").forEach(el => el.classList.toggle("hidden", donor));
  loadOffers();
  if (donor) loadMyOffers(); else loadRedistributions();
}

async function loadOffers() {
  const params = new URLSearchParams({ size: "50" });
  if ($("filter-category").value) params.set("category", $("filter-category").value);
  if ($("filter-city").value.trim()) params.set("city", $("filter-city").value.trim());
  try {
    const page = await request("GET", "/portal/v1/offers?" + params);
    $("offers").innerHTML = page.items.map(offer => `
      <tr><td><strong>${esc(offer.description)}</strong><br><span class="muted">${esc(CATEGORIES[offer.foodCategory] || offer.foodCategory)}</span></td>
        <td>${esc(offer.quantity)} ${esc(offer.unit)}</td><td>${esc(offer.donorName)}</td><td>${esc(offer.city)}</td>
        <td>${when(offer.availableUntil)}</td>
        <td>${offer._links?.["start-redistribution"] ? `<button class="small" data-offer="${esc(offer.id)}">Solicitar</button>` : ""}</td></tr>`).join("")
      || `<tr><td colspan="6" class="muted">Nenhuma oferta disponível.</td></tr>`;
    show($("offers-msg"), "");
  } catch (error) { show($("offers-msg"), describeError(error), "error"); }
}

async function loadMyOffers() {
  try {
    const page = await request("GET", "/portal/v1/my-offers?size=50");
    $("my-offers").innerHTML = page.items.map(offer => `
      <tr><td>${esc(offer.description)}</td><td>${esc(offer.quantity)} ${esc(offer.unit)}</td>
        <td><span class="badge ${esc(offer.status)}">${esc(offer.status)}</span></td><td>${when(offer.availableUntil)}</td></tr>`).join("")
      || `<tr><td colspan="4" class="muted">Você ainda não publicou ofertas.</td></tr>`;
  } catch (error) { show($("offer-msg"), describeError(error), "error"); }
}

async function loadRedistributions() {
  try {
    const page = await request("GET", "/portal/v1/redistributions?size=50");
    $("redistributions").innerHTML = page.items.map(item => `
      <tr><td class="mono">${esc(item.id)}</td><td><span class="badge ${esc(item.status)}">${esc(item.statusDescription)}</span></td>
        <td>${when(item.createdAt)}</td><td><button class="small secondary" data-follow="${esc(item.id)}">Acompanhar</button></td></tr>`).join("")
      || `<tr><td colspan="4" class="muted">Nenhuma redistribuição ainda.</td></tr>`;
  } catch (error) { show($("redistribution-msg"), describeError(error), "error"); }
}

async function follow(id) {
  try {
    const r = await request("GET", `/portal/v1/redistributions/${encodeURIComponent(id)}`);
    $("detail-body").innerHTML = `
      <p><span class="badge ${esc(r.status)}">${esc(r.statusDescription)}</span>
        ${r.problem ? `<span class="msg error" style="display:block">${esc(r.problem)}</span>` : ""}</p>
      <table>
        <tr><th>Oferta</th><td>${esc(r.offer?.description)} · ${esc(r.offer?.quantity)} ${esc(r.offer?.unit)} · ${esc(r.offer?.city)}</td></tr>
        <tr><th>Entrega</th><td><span class="badge ${esc(r.delivery?.status)}">${esc(r.delivery?.status)}</span></td></tr>
        <tr><th>Coleta em</th><td>${esc(r.delivery?.pickupAddress)}</td></tr>
        <tr><th>Entrega em</th><td>${esc(r.delivery?.deliveryAddress)}</td></tr>
        <tr><th>Coletada / entregue</th><td>${when(r.delivery?.collectedAt) || "—"} / ${when(r.delivery?.deliveredAt) || "—"}</td></tr>
      </table>
      <div class="row" style="margin-top:10px">
        <button class="small secondary" data-follow="${esc(r.id)}">Atualizar</button>
        ${r._links?.cancel ? `<button class="small" data-cancel="${esc(r.id)}">Cancelar redistribuição</button>` : ""}
      </div>`;
    $("redistribution-detail").classList.remove("hidden");
  } catch (error) { show($("redistribution-msg"), describeError(error), "error"); }
}

$("login-form").addEventListener("submit", async event => {
  event.preventDefault();
  const form = new FormData(event.target);
  try { await login(form.get("role"), form.get("organizationId").trim()); render(); }
  catch (error) { show($("login-msg"), describeError(error), "error"); }
});

$("logout").addEventListener("click", () => { saveSession(null); render(); });
$("refresh-offers").addEventListener("click", loadOffers);
$("refresh-redistributions").addEventListener("click", loadRedistributions);

$("offer-form").addEventListener("submit", async event => {
  event.preventDefault();
  const form = new FormData(event.target);
  const body = {
    foodCategory: form.get("foodCategory"), description: form.get("description"),
    quantity: Number(form.get("quantity")), unit: form.get("unit"),
    availableFrom: new Date().toISOString(), availableUntil: new Date(form.get("availableUntil")).toISOString(),
  };
  try {
    const offer = await request("POST", "/portal/v1/offers", body);
    show($("offer-msg"), `Oferta publicada (${offer.id}). Ela aparece na lista de disponíveis assim que o evento chega ao modelo de leitura.`);
    loadMyOffers();
    setTimeout(loadOffers, 2000);
  } catch (error) { show($("offer-msg"), describeError(error), "error"); }
});

document.addEventListener("click", async event => {
  const offerId = event.target.dataset.offer, followId = event.target.dataset.follow, cancelId = event.target.dataset.cancel;
  if (offerId) {
    event.target.disabled = true;
    try {
      const r = await request("POST", "/portal/v1/redistributions", { offerId });
      show($("redistribution-msg"), r.status === "FAILED" ? `Não foi possível concluir: ${r.problem}` : `Redistribuição criada: ${r.statusDescription}.`,
        r.status === "FAILED" ? "error" : "ok");
      loadOffers(); loadRedistributions(); follow(r.id);
    } catch (error) { show($("offers-msg"), describeError(error), "error"); event.target.disabled = false; }
  }
  if (followId) follow(followId);
  if (cancelId && confirm("Cancelar esta redistribuição? A oferta volta a ficar disponível.")) {
    try { await request("POST", `/portal/v1/redistributions/${encodeURIComponent(cancelId)}/cancellation`); loadRedistributions(); follow(cancelId); }
    catch (error) { show($("redistribution-msg"), describeError(error), "error"); }
  }
});

$("assistant-form").addEventListener("submit", async event => {
  event.preventDefault();
  const question = new FormData(event.target).get("question");
  $("ask").disabled = true;
  $("assistant-status").textContent = "Consultando...";
  show($("assistant-msg"), "");
  try {
    const r = await request("POST", "/portal/v1/assistant/questions", { question });
    $("assistant-answer").innerHTML = `
      <p><span class="badge ${esc(r.mode)}">${r.mode === "llm" ? "resposta do assistente" : "assistente indisponível: trechos da base"}</span>
        <span class="muted">${esc(r.latencyMs)} ms</span></p>
      <div class="answer">${esc(r.answer)}</div>
      ${r.sources?.length ? `<p class="muted"><strong>Fontes:</strong><br>${r.sources.map(s => `[${esc(s.ref)}] ${esc(s.title)} — ${esc(s.section)}`).join("<br>")}</p>` : ""}
      ${r.consultedData?.length ? `<p class="muted"><strong>Dados consultados:</strong> ${r.consultedData.map(c => `${esc(c.tool)} (${esc(c.results ?? 0)} resultados)`).join(", ")}</p>` : ""}`;
    $("assistant-answer").classList.remove("hidden");
  } catch (error) { show($("assistant-msg"), describeError(error), "error"); }
  finally { $("ask").disabled = false; $("assistant-status").textContent = ""; }
});

fillCategories();
render();
