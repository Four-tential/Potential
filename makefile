run:
	docker compose --env-file .env up -d

smee:
	node smee.js

down:
	docker compose down

down-v:
	docker compose down -v