// Painel administrativo: fala só com o Admin BFF (/admin/v1), pelo gateway.
const $ = id => document.getElementById(id);
const { request, login, session, saveSession, describeError, show, esc, when, CATEGORIES } = Ravita;

function render() {
  const current = session();
  $("login-section").classList.toggle("hidden", !!current);
  $("app").classList.toggle("hidden", !current);
  $("logout").classList.toggle("hidden", !current);
  $("who").textContent = current ? "Admin" : "";
  if (current) { loadDashboard(); loadRedistributions(); loadLogistics(); loadOrganizations(); }
}

function counts(title, values) {
  return Object.entries(values || {}).filter(([, n]) => n > 0)
    .map(([status, n]) => `<div class="card"><div class="muted">${esc(title)} · ${esc(status)}</div><div class="n">${esc(n)}</div></div>`).join("");
}

async function loadDashboard() {
  try {
    const d = await request("GET", "/admin/v1/dashboard");
    $("dashboard").innerHTML = `
      <div class="card"><div class="muted">Doadores</div><div class="n">${esc(d.organizations.donors)}</div></div>
      <div class="card"><div class="muted">Instituições</div><div class="n">${esc(d.organizations.institutions)}</div></div>
      <div class="card"><div class="muted">Ofertas</div><div class="n">${esc(d.offers.total)}</div></div>
      <div class="card"><div class="muted">Redistribuições</div><div class="n">${esc(d.redistributions.total)}</div></div>
      ${counts("Ofertas", d.offers.byStatus)}${counts("Redistribuições", d.redistributions.byStatus)}`;
    $("generated").textContent = "Atualizado em " + when(d.generatedAt);
  } catch (error) { $("dashboard").innerHTML = `<div class="msg error">${esc(describeError(error))}</div>`; }
}

async function loadRedistributions() {
  const status = $("status-filter").value;
  try {
    const page = await request("GET", "/admin/v1/redistributions?size=50" + (status ? "&status=" + status : ""));
    $("redistributions").innerHTML = page.items.map(r => `
      <tr><td class="mono">${esc(r.id)}<br><span class="muted">corr: ${esc(r.correlationId)}</span></td>
        <td><span class="badge ${esc(r.status)}">${esc(r.status)}</span></td><td>${esc(r.sagaState)}</td><td>${esc(r.lastEvent)}</td>
        <td>${esc(r.failureReason || "")}</td><td><button class="small secondary" data-detail="${esc(r.id)}">Diagnóstico</button></td></tr>`).join("")
      || `<tr><td colspan="6" class="muted">Nenhuma redistribuição.</td></tr>`;
  } catch (error) { show($("redistribution-msg"), describeError(error), "error"); }
}

async function detail(id) {
  try {
    const r = await request("GET", `/admin/v1/redistributions/${encodeURIComponent(id)}`);
    $("detail-body").innerHTML = `
      <table>
        <tr><th>Estado da SAGA</th><td><span class="badge ${esc(r.status)}">${esc(r.sagaState)}</span></td></tr>
        <tr><th>Compensação</th><td>${esc(r.compensation)}</td></tr>
        <tr><th>Último evento</th><td>${esc(r.lastEvent)}</td></tr>
        <tr><th>Correlation ID</th><td class="mono">${esc(r.correlationId)}</td></tr>
        <tr><th>Motivo da falha</th><td>${esc(r.failureReason || "—")}</td></tr>
        <tr><th>Oferta / doador</th><td class="mono">${esc(r.offerId)} · ${esc(r.donorId)} · ${esc(r.offer?.status)}</td></tr>
        <tr><th>Instituição</th><td class="mono">${esc(r.institutionId)}</td></tr>
        <tr><th>Reserva / logística</th><td class="mono">${esc(r.reservationId || "—")} · ${esc(r.logisticsId || "—")} · ${esc(r.logistics?.status || "")}</td></tr>
      </table>
      <details style="margin-top:8px"><summary class="muted">Resposta completa do Admin BFF</summary><pre>${esc(JSON.stringify(r, null, 2))}</pre></details>`;
    $("detail").classList.remove("hidden");
  } catch (error) { show($("redistribution-msg"), describeError(error), "error"); }
}

async function loadLogistics() {
  const status = $("logistics-filter").value;
  try {
    const page = await request("GET", "/admin/v1/logistics?size=50" + (status ? "&status=" + status : ""));
    $("logistics").innerHTML = page.items.map(op => `
      <tr><td class="mono">${esc(op.id)}</td><td>${esc(op.pickupAddress)}</td><td>${esc(op.deliveryAddress)}</td>
        <td><span class="badge ${esc(op.status)}">${esc(op.status)}</span></td>
        <td>${op.status === "SCHEDULED" ? `<button class="small" data-collect="${esc(op.id)}">Registrar coleta</button>` : ""}
            ${op.status === "COLLECTED" ? `<button class="small" data-deliver="${esc(op.id)}">Registrar entrega</button>` : ""}</td></tr>`).join("")
      || `<tr><td colspan="5" class="muted">Nenhuma operação com esse status.</td></tr>`;
  } catch (error) { show($("logistics-msg"), describeError(error), "error"); }
}

async function loadOrganizations() {
  try {
    const page = await request("GET", "/admin/v1/organizations?size=100");
    $("organizations").innerHTML = page.items.map(o => `
      <tr><td>${esc(o.name)}</td><td>${o.type === "DONOR" ? "Doador" : "Instituição"}</td><td>${esc(o.address?.city)}</td>
        <td class="mono">${esc(o.id)}</td></tr>`).join("") || `<tr><td colspan="4" class="muted">Nenhuma organização.</td></tr>`;
  } catch (error) { show($("organization-msg"), describeError(error), "error"); }
}

function toggleCapacity() {
  document.querySelectorAll(".capacity").forEach(el => el.classList.toggle("hidden", $("org-type").value !== "INSTITUTION"));
}

$("login").addEventListener("click", async () => {
  try { await login("ADMIN"); render(); } catch (error) { show($("login-msg"), describeError(error), "error"); }
});
$("logout").addEventListener("click", () => { saveSession(null); render(); });
$("refresh-dashboard").addEventListener("click", loadDashboard);
$("refresh-redistributions").addEventListener("click", loadRedistributions);
$("status-filter").addEventListener("change", loadRedistributions);
$("refresh-logistics").addEventListener("click", loadLogistics);
$("logistics-filter").addEventListener("change", loadLogistics);
$("org-type").addEventListener("change", toggleCapacity);

document.addEventListener("click", async event => {
  const { detail: detailId, collect, deliver } = event.target.dataset;
  if (detailId) detail(detailId);
  if (collect || deliver) {
    event.target.disabled = true;
    try {
      await request("POST", `/admin/v1/logistics/${encodeURIComponent(collect || deliver)}/${collect ? "collection" : "delivery"}`);
      show($("logistics-msg"), collect ? "Coleta registrada: a SAGA avança para IN_TRANSIT pelo evento." : "Entrega registrada: a SAGA conclui pelo evento.");
      loadLogistics();
      setTimeout(() => { loadRedistributions(); loadDashboard(); }, 1500);
    } catch (error) { show($("logistics-msg"), describeError(error), "error"); event.target.disabled = false; }
  }
});

$("organization-form").addEventListener("submit", async event => {
  event.preventDefault();
  const f = new FormData(event.target);
  const body = {
    name: f.get("name"), type: f.get("type"), document: f.get("document"),
    address: { street: f.get("street"), number: f.get("number"), district: f.get("district"), city: f.get("city"),
               state: f.get("state"), postalCode: f.get("postalCode") },
  };
  if (body.type === "INSTITUTION") {
    body.receivingCapacity = [{ foodCategory: f.get("capacityCategory"), maxQuantity: Number(f.get("maxQuantity")), unit: f.get("capacityUnit") }];
  }
  try {
    const created = await request("POST", "/admin/v1/organizations", body);
    show($("organization-msg"), `Cadastrada. Id para entrar no portal: ${created.id}`);
    loadOrganizations(); loadDashboard();
  } catch (error) { show($("organization-msg"), describeError(error), "error"); }
});

for (const [code, label] of Object.entries(CATEGORIES)) $("capacity-category").add(new Option(label, code));
toggleCapacity();
render();
