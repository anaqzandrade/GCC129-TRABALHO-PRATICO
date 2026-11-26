# =====================================================================
# Ravita - verificação final (etapa 10): tudo o que o enunciado pede para demonstrar, pelo API Gateway
#
# Antes: .env com GOOGLE_API_KEY (e, se quiser, JWT_SECRET). Na raiz do repositório:
#   docker compose down -v                 (opcional: começa com os bancos vazios)
#   docker compose up -d --build
#   Set-ExecutionPolicy -Scope Process Bypass
#   .\scripts\verificacao.ps1
#
# Leva cerca de 5 minutos. O script para e religa o logistics-service e o RabbitMQ, e recria o
# assistant-service com uma chave inválida, voltando tudo ao normal no fim de cada seção.
# =====================================================================

Set-Location (Split-Path -Parent $PSScriptRoot)   # raiz do repositório
try { [Console]::OutputEncoding = [Text.Encoding]::UTF8 } catch { }
Add-Type -AssemblyName System.Net.Http
$http = [System.Net.Http.HttpClient]::new()
$http.Timeout = [TimeSpan]::FromSeconds(45)
$gw = 'http://localhost:8000'
$script:ok = 0
$script:falhas = 0

# Api <MÉTODO> <caminho> [token] [corpo] [correlation id] -> { Status, Body, Correlation }
function Api([string]$Method, [string]$Path, [string]$Token, $Body = $null, [string]$Correlation = $null) {
    $url = if ($Path -like 'http*') { $Path } else { "$gw$Path" }
    $req = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::new($Method), $url)
    $req.Headers.ExpectContinue = $false
    if ($Token) { [void]$req.Headers.TryAddWithoutValidation('Authorization', "Bearer $Token") }
    if ($Correlation) { [void]$req.Headers.TryAddWithoutValidation('X-Correlation-ID', $Correlation) }
    if ($null -ne $Body) {
        $req.Content = [System.Net.Http.StringContent]::new(($Body | ConvertTo-Json -Depth 10), [Text.Encoding]::UTF8, 'application/json')
    }
    try {
        $resp = $http.SendAsync($req).GetAwaiter().GetResult()
    } catch {
        Write-Host "$Method $Path -> sem resposta (o compose está no ar?)" -ForegroundColor Red
        return [pscustomobject]@{ Status = 0; Body = $null; Correlation = $null }
    }
    $texto = [Text.Encoding]::UTF8.GetString($resp.Content.ReadAsByteArrayAsync().GetAwaiter().GetResult())
    $json = $null
    if ($texto) { try { $json = $texto | ConvertFrom-Json } catch { } }
    $corr = $resp.Headers | Where-Object { $_.Key -eq 'X-Correlation-ID' } | ForEach-Object { @($_.Value)[0] }
    [pscustomobject]@{ Status = [int]$resp.StatusCode; Body = $json; Correlation = $corr }
}

function Check([string]$Descricao, $Condicao, $Detalhe = '') {
    if ($Condicao) {
        $script:ok++
        Write-Host "  OK      $Descricao" -ForegroundColor Green
    } else {
        $script:falhas++
        Write-Host "  FALHOU  $Descricao  $Detalhe" -ForegroundColor Red
    }
}

function Titulo([string]$Texto) { Write-Host "`n== $Texto" -ForegroundColor Cyan }

function Mostrar([string]$Rotulo, $Objeto) {
    Write-Host "`n$Rotulo" -ForegroundColor Yellow
    Write-Host ($Objeto | ConvertTo-Json -Depth 8)
}

function Token([string]$Role, [string]$Org) {
    $corpo = [ordered]@{ role = $Role }
    if ($Org) { $corpo.organizationId = $Org }
    (Api POST '/auth/token' $null $corpo).Body.accessToken
}

# repete a condição por até $Segundos (os eventos chegam pelo RabbitMQ, não na hora)
function Esperar([scriptblock]$Condicao, [int]$Segundos = 20) {
    $limite = (Get-Date).AddSeconds($Segundos)
    while ((Get-Date) -lt $limite) {
        if (& $Condicao) { return $true }
        Start-Sleep -Seconds 1   # o gateway limita a 2 requisições por segundo por token
    }
    return $false
}

function Documento { -join (1..14 | ForEach-Object { Get-Random -Maximum 10 }) }
function Tem($Objeto, [string]$Campo) { $null -ne $Objeto -and $null -ne $Objeto.PSObject.Properties[$Campo] }
# consulta no banco de um serviço; o SQL vai pela entrada padrão (sem aspas aninhadas na linha de comando)
function Psql([string]$Servico, [string]$Sql) {
    "$($Sql | docker compose exec -T $Servico sh -c 'psql -q -t -A -U $POSTGRES_USER -d $POSTGRES_DB' 2>&1)".Trim()
}
function Saudavel([string[]]$Servicos, [int]$Segundos = 120) {
    Esperar {
        $l = @(docker compose ps --format '{{.Service}} {{.Health}}')
        @($Servicos | Where-Object { $l -notcontains "$_ healthy" }).Count -eq 0
    } $Segundos
}
function NaListaDoPortal([string]$Id, [string]$Token) {
    $p = Api GET '/portal/v1/offers?size=100' $Token
    $ultima = [math]::Max(1, [math]::Ceiling($p.Body.total / 100))
    if ($ultima -gt 1) { $p = Api GET "/portal/v1/offers?size=100&page=$ultima" $Token }
    @($p.Body.items | Where-Object { $_.id -eq $Id }).Count -eq 1
}

$inicio = (Get-Date).ToString("yyyy-MM-dd'T'HH:mm:sszzz")
$fim    = (Get-Date).AddHours(6).ToString("yyyy-MM-dd'T'HH:mm:sszzz")
function Oferta([string]$Descricao, [string]$Categoria = 'VEGETABLES') {
    [ordered]@{ foodCategory = $Categoria; description = $Descricao; quantity = 30; unit = 'KG'
                availableFrom = $inicio; availableUntil = $fim }
}


# ---------------------------------------------------------------- 1. containers, portas e redes
Titulo '1. Containers, portas e redes'
Write-Host '  aguardando os healthchecks (até 150 s)...' -ForegroundColor DarkGray
$todos = @(docker compose config --services)
[void](Saudavel $todos 150)
$linhas = @(docker compose ps --format '{{.Service}} {{.Health}}')
Check "17 containers no ar (encontrados: $($linhas.Count))" ($linhas.Count -eq 17)
$doentes = @($todos | Where-Object { $linhas -notcontains "$_ healthy" })
Check 'todos healthy' ($doentes.Count -eq 0) ($doentes -join ', ')
$publicadas = @(docker compose ps --format '{{.Service}} {{.Ports}}' | Where-Object { $_ -match '->' })
Check 'só o api-gateway publica porta no host' ($publicadas.Count -eq 1 -and $publicadas[0] -like 'api-gateway *') ($publicadas -join ' | ')
$fechada = $true
try { [void]$http.GetAsync('http://localhost:8082/api/v1/offers').GetAwaiter().GetResult(); $fechada = $false } catch { }
Check 'http://localhost:8082/api/v1/offers falha (porta do offer-service fechada)' $fechada
$saida = docker compose exec -T api-gateway wget -qO- -T 3 http://offer-service:8082/actuator/health 2>&1
Check 'api-gateway NÃO alcança o offer-service (bad address)' ("$saida" -match 'bad address')
foreach ($rede in 'organization', 'offer', 'matching', 'logistics', 'redistribution', 'assistant') {
    $membros = @((docker network inspect "ravita_$rede-data" --format '{{range .Containers}}{{.Name}} {{end}}').Trim() -split '\s+' | Where-Object { $_ })
    Check "rede $rede-data só com $rede-service e $rede-db" ($membros.Count -eq 2) ($membros -join ', ')
}
$bancos = Psql 'offer-db' "select string_agg(datname, ',') from pg_database where datname like '%_db'"
Check "offer-db só tem o próprio banco ($bancos)" ($bancos -eq 'offer_db')
$r = Api GET '/'
Check 'http://localhost:8000/ leva ao portal' ($r.Status -eq 200 -and $http.GetStringAsync("$gw/app/portal/").GetAwaiter().GetResult() -match 'Portal de doadores')
$adminHtml = $http.GetStringAsync("$gw/app/admin/").GetAwaiter().GetResult()
Check 'página do admin em /app/admin/' ($adminHtml -match 'Painel administrativo')


# ---------------------------------------------------------------- 2. cadastros pelo Admin
Titulo '2. Cadastros pelo Admin (gateway -> Admin BFF -> organization-service)'
$admin = Token 'ADMIN'
$sufixo = Get-Random -Minimum 100 -Maximum 999
$r = Api POST '/admin/v1/organizations' $admin ([ordered]@{
    name = "Mercado Central $sufixo"; type = 'DONOR'; document = (Documento)
    address = [ordered]@{ street = 'Rua Central'; number = '100'; district = 'Centro'; city = 'Lavras'; state = 'MG'; postalCode = '37200000' }
})
Check 'doador cadastrado (201)' ($r.Status -eq 201) "$($r.Status) $($r.Body.code)"
$doadorId = $r.Body.id
$instituicaoNome = "Casa Esperança $sufixo"
$r = Api POST '/admin/v1/organizations' $admin ([ordered]@{
    name = $instituicaoNome; type = 'INSTITUTION'; document = (Documento)
    address = [ordered]@{ street = 'Rua das Flores'; number = '50'; district = 'Centro'; city = 'Lavras'; state = 'MG'; postalCode = '37200000' }
    receivingCapacity = @( [ordered]@{ foodCategory = 'VEGETABLES'; maxQuantity = 50; unit = 'KG' },
                           [ordered]@{ foodCategory = 'DAIRY'; maxQuantity = 40; unit = 'L' } )
})
Check 'instituição cadastrada (201)' ($r.Status -eq 201) "$($r.Status) $($r.Body.code)"
$instituicaoId = $r.Body.id
if (-not $doadorId -or -not $instituicaoId) { Write-Host "`nSem os cadastros, o resto falharia em cascata. Parando." -ForegroundColor Red; exit 1 }
$doador = Token 'DONOR' $doadorId
$instituicao = Token 'INSTITUTION' $instituicaoId


# ---------------------------------------------------------------- 3. ponta a ponta e CQRS
Titulo '3. Ponta a ponta e CQRS: oferta criada no Portal aparece no Portal e no Admin'
$antes = (Api GET '/admin/v1/dashboard' $admin).Body.offers.total
$r = Api POST '/portal/v1/offers' $doador (Oferta 'Hortaliças variadas')
Check 'doador publica oferta pelo Portal (201)' ($r.Status -eq 201) "$($r.Status) $($r.Body.code)"
$oferta1 = $r.Body.id
Check 'a oferta aparece em GET /portal/v1/offers, que lê o modelo de leitura' (Esperar { NaListaDoPortal $oferta1 $instituicao })
$projecao = Psql 'offer-db' "select status || ' ' || last_event_type from available_offers_read where offer_id = '$oferta1'"
Check "projeção em available_offers_read ($projecao)" ($projecao -eq 'AVAILABLE OfferCreated')
$depois = (Api GET '/admin/v1/dashboard' $admin).Body.offers.total
Check "o dashboard do Admin conta a oferta nova (total: $antes -> $depois)" ($depois -eq $antes + 1)


# ---------------------------------------------------------------- 4. SAGA: caminho feliz
Titulo '4. SAGA: caminho feliz até COMPLETED'
$r = Api POST '/portal/v1/redistributions' $instituicao ([ordered]@{ offerId = $oferta1 }) 'final-saga-ok'
Check 'instituição inicia a redistribuição (LOGISTICS_PENDING)' ($r.Status -eq 201 -and $r.Body.status -eq 'LOGISTICS_PENDING') "$($r.Status) $($r.Body.status)"
$red1 = $r.Body.id
$diag = (Api GET "/admin/v1/redistributions/$red1" $admin).Body
$r = Api POST "/admin/v1/logistics/$($diag.logisticsId)/collection" $admin
Check 'Admin registra a coleta' ($r.Status -eq 200) $r.Status
Check 'Portal: IN_TRANSIT pelo evento logistics.food.collected' (Esperar { (Api GET "/portal/v1/redistributions/$red1" $instituicao).Body.status -eq 'IN_TRANSIT' })
$r = Api POST "/admin/v1/logistics/$($diag.logisticsId)/delivery" $admin
Check 'Admin registra a entrega' ($r.Status -eq 200) $r.Status
Check 'Admin: sagaState COMPLETED' (Esperar { (Api GET "/admin/v1/redistributions/$red1" $admin).Body.sagaState -eq 'COMPLETED' })


# ---------------------------------------------------------------- 5. SAGA: falha e compensação
Titulo '5. SAGA: falha com compensação (logistics-service parado)'
$oferta2 = (Api POST '/portal/v1/offers' $doador (Oferta 'Frutas da estação' 'FRUITS')).Body.id
docker compose stop logistics-service 2>&1 | Out-Null
$r = Api POST '/portal/v1/redistributions' $instituicao ([ordered]@{ offerId = $oferta2 }) 'final-saga-falha'
Check 'redistribuição termina FAILED' ($r.Body.status -eq 'FAILED') "$($r.Status) $($r.Body.status)"
Check "Portal explica o problema ($($r.Body.problem))" ($r.Body.problem -like 'Não foi possível agendar a coleta*')
$diag = (Api GET "/admin/v1/redistributions/$($r.Body.id)" $admin).Body
Check 'Admin: compensation DONE, lastEvent ReservationReleased' ($diag.compensation -eq 'DONE' -and $diag.lastEvent -eq 'ReservationReleased')
Check 'Admin: motivo da falha visível' ($diag.failureReason -like '*compensação executada*') $diag.failureReason
Check 'a reserva foi liberada: oferta AVAILABLE de novo' ($diag.offer.status -eq 'AVAILABLE') $diag.offer.status
docker compose start logistics-service 2>&1 | Out-Null


# ---------------------------------------------------------------- 6. outbox
Titulo '6. Outbox: RabbitMQ parado não impede criar a oferta'
docker compose stop rabbitmq 2>&1 | Out-Null
$r = Api POST '/portal/v1/offers' $doador (Oferta 'Cenouras e beterrabas')
Check 'com o RabbitMQ parado, a oferta é criada (201)' ($r.Status -eq 201) "$($r.Status) $($r.Body.code)"
$oferta3 = $r.Body.id
Start-Sleep -Seconds 3
$estado = Psql 'offer-db' "select status from outbox_events where aggregate_id = '$oferta3'"
Check "evento OfferCreated fica PENDING no outbox_events ($estado)" ($estado -eq 'PENDING')
Check 'a oferta ainda não está no modelo de leitura (o evento não saiu)' (-not (NaListaDoPortal $oferta3 $instituicao))
docker compose start rabbitmq 2>&1 | Out-Null
Write-Host '  RabbitMQ religado; aguardando o publicador do outbox (até 90 s)...' -ForegroundColor DarkGray
Check 'evento PUBLISHED depois que o RabbitMQ volta' (Esperar { (Psql 'offer-db' "select status from outbox_events where aggregate_id = '$oferta3'") -eq 'PUBLISHED' } 90)
Check 'a oferta chega ao modelo de leitura' (Esperar { NaListaDoPortal $oferta3 $instituicao } 30)
Check 'a oferta chega ao demand-matching (available_offers)' (Esperar { (Psql 'matching-db' "select count(*) from available_offers where offer_id = '$oferta3'") -eq '1' } 30)
[void](Saudavel @('rabbitmq', 'redistribution-service', 'logistics-service', 'demand-matching-service') 90)


# ---------------------------------------------------------------- 7. LLM
Titulo '7. Assistente de doação (LLM)'
$r = Api POST '/portal/v1/assistant/questions' $doador ([ordered]@{ question = 'Tenho iogurtes que vencem em 3 dias. Posso doar? Quais instituições de Lavras recebem laticínios?' }) 'final-llm'
$p = $r.Body
Check 'pergunta pelo Portal respondida pelo modelo (mode llm)' ($r.Status -eq 200 -and $p.mode -eq 'llm') "$($r.Status) $($p.mode) $($p.notice) $($p.code)"
Check 'resposta com fontes da base de conhecimento' (@($p.sources).Count -ge 1 -and $p.answer -match '\[\d')
Check 'o modelo usou a tool instituicoes_que_aceitam' (@($p.consultedData | Where-Object { $_.tool -eq 'instituicoes_que_aceitam' -and $_.results -ge 1 }).Count -ge 1)
Check "a resposta cita a instituição real ($instituicaoNome)" ($p.answer -like "*$instituicaoNome*")
Write-Host ("  " + (($p.answer -split "`n") -join "`n  ")) -ForegroundColor DarkGray


# ---------------------------------------------------------------- 8. correlation id
# conferido antes de recriar o assistant-service na seção 9: os logs vão embora junto com o container
Titulo '8. Correlation ID de ponta a ponta'
$logs = @(docker compose logs --no-color --since 15m api-gateway portal-bff assistant-service | Select-String 'final-llm' | ForEach-Object { $_.Line })
foreach ($s in 'api-gateway', 'portal-bff', 'assistant-service') {
    Check "final-llm nos logs do $s" (@($logs | Where-Object { $_ -like "$s*" }).Count -ge 1)
}


# ---------------------------------------------------------------- 9. fallback e circuit breaker
Titulo '9. Chave inválida: fallback e circuit breaker'
$env:GOOGLE_API_KEY = 'chave-invalida-para-teste'
docker compose up -d assistant-service 2>&1 | Out-Null
[void](Saudavel @('assistant-service') 90)
Start-Sleep -Seconds 7   # o portal-bff guarda o IP do assistente por até 5 s
$respostas = @()
foreach ($i in 1..4) {
    $respostas += (Api POST '/portal/v1/assistant/questions' $doador ([ordered]@{ question = 'Uma refeição pronta pode ficar quanto tempo aquecida?' }) "final-fallback-$i").Body
}
Check 'com a chave inválida, as 4 respostas saem pelo fallback' (@($respostas | Where-Object { $_.mode -eq 'fallback' }).Count -eq 4) (($respostas | ForEach-Object { "$($_.mode)$($_.code)" }) -join ',')
Check 'o fallback devolve os trechos da base' ($respostas[0].answer -match '6 horas')
Check 'na 4ª pergunta o circuito já estava aberto' ($respostas[3].notice -match 'circuito aberto') $respostas[3].notice
$aberturas = @(docker compose logs --no-color --since 5m assistant-service | Select-String 'Circuit breaker' | ForEach-Object { $_.Line })
Check 'o log registra o circuito abrindo' (@($aberturas | Where-Object { $_ -match '-> open' }).Count -ge 1)
Remove-Item Env:GOOGLE_API_KEY
docker compose up -d assistant-service 2>&1 | Out-Null
[void](Saudavel @('assistant-service') 90)
$doc07 = Get-Content docs/arquitetura/07-llm.md -Raw -Encoding UTF8
Check 'doc 07 com a avaliação, a latência e o custo' ($doc07 -match 'Cobertura média' -and $doc07 -match 'Custo por pergunta')


# ---------------------------------------------------------------- resultado
Write-Host ''
if ($script:falhas -eq 0) {
    Write-Host "Verificação final concluída: $($script:ok) verificações passaram." -ForegroundColor Green
} else {
    Write-Host "$($script:falhas) de $($script:ok + $script:falhas) verificações falharam (linhas FALHOU acima)." -ForegroundColor Red
}
