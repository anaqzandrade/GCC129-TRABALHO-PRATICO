# Clientes do sistema (doc 02, seção 14): páginas estáticas servidas pelo nginx.
# O navegador acessa pelo API Gateway (http://localhost:8000/app/...), na mesma origem da API.
FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY shared /usr/share/nginx/html/shared
COPY portal /usr/share/nginx/html/portal
COPY admin /usr/share/nginx/html/admin
EXPOSE 80
