# Trade Bot Frontend

Modern React + TypeScript frontend for managing trading bots.

## Features

- **Bot Management**: Create, load, start, and resume trading bots
- **Real-time Dashboard**: Monitor active bots with live position data
- **Type-safe**: Full TypeScript support with strict type checking
- **Modern UI**: Built with Tailwind CSS for responsive design
- **API Integration**: Axios-based service layer for REST API communication

## Tech Stack

- React 18
- TypeScript 5
- Vite 5
- Tailwind CSS 3
- Axios
- React Router v6

## Development

### Prerequisites

- Node.js 18+
- npm 9+

### Installation

```bash
npm install
```

### Run Development Server

```bash
npm run dev
```

The dev server will start on `http://localhost:5173` with API proxying to `http://localhost:8080`.

### Build for Production

```bash
npm run build
```

Output will be in the `dist/` directory.

### Type Check

```bash
npm run type-check
```

### Lint

```bash
npm run lint
```

## Docker Deployment

### Build and Run with Docker Compose

From project root:

```bash
# Build and start all services
docker-compose up -d --build

# View logs
docker-compose logs -f frontend

# Stop services
docker-compose down
```

### Using Deployment Script

```bash
# From project root
./deploy_frontend.sh
```

This script will:
1. Build the backend (Gradle)
2. Build the frontend (npm)
3. Stop existing containers
4. Rebuild Docker images
5. Start all services

## Project Structure

```
frontend/
├── nginx/
│   ├── Dockerfile          # Multi-stage Docker build
│   └── nginx.conf          # Nginx reverse proxy config
├── src/
│   ├── types/              # TypeScript type definitions
│   ├── services/           # API service layer
│   ├── hooks/              # Custom React hooks
│   ├── components/         # React components (future)
│   ├── pages/              # Route pages
│   ├── assets/styles/      # Global CSS
│   └── utils/              # Utility functions
├── public/                 # Static assets
├── vite.config.ts          # Vite configuration
├── tailwind.config.js      # Tailwind CSS configuration
├── tsconfig.json           # TypeScript configuration
└── package.json            # Dependencies and scripts
```

## API Endpoints

### Bot Management

- `POST /api/create_bot` - Create new bot
- `POST /api/load_bot` - Load bot from file
- `POST /api/start_bot` - Start bot with validation
- `POST /api/resume_bot` - Resume bot without clearing data
- `GET /api/positions` - Get all bot positions

### Orders & Emulation

- `GET /api/orders?botName={name}` - Get orders for specific bot
- `POST /api/emulate` - Run backtesting
- `POST /api/get_necessary_balance` - Calculate required balance

## Environment Variables

### Development (`.env.development`)

```
VITE_API_URL=http://localhost:8080
VITE_POLLING_INTERVAL=5000
```

### Production (`.env.production`)

```
VITE_API_URL=/api
VITE_POLLING_INTERVAL=10000
```

## Color Scheme

Matching existing trade bot UI:

- **Buy Orders**: Light green (`#71ff788e`)
- **Sell Orders**: Light red (`#ff71718a`)
- **Primary**: Blue theme
- **Background**: Gray-50

## Pages

### Dashboard (`/`)

- Displays all active bots in a grid
- Real-time position data with polling
- Shows PnL with color coding (green/red)

### Bot Management (`/bots`)

- Load bot from file
- Start bot (with balance validation)
- Resume bot (without clearing data)
- Instructions and help text

## Future Enhancements

### Planned Features

1. **Create Bot Form**: Visual form for creating Grid/Trader bots
2. **Bot Details Page**: Detailed view with orders table
3. **Settings Editor**: JSON editor with validation
4. **Order History**: Complete order management
5. **Emulation Interface**: Parameter optimization UI
6. **WebSocket Support**: True real-time updates

### Planned Components

- BotCard - Individual bot display card
- BotStatus - Status badge component
- OrdersTable - Color-coded orders display
- GridSettingsForm - Grid bot creation
- TraderSettingsForm - Trader bot creation
- JsonEditor - Advanced settings editor

## Troubleshooting

### API Connection Issues

If the frontend can't connect to the backend:

1. Check backend is running: `docker-compose ps`
2. Check Nginx proxy config: `frontend/nginx/nginx.conf`
3. Verify network: `docker network ls` (should see `trade-bot-network`)

### Build Fails

Common issues:

- **TypeScript errors**: Run `npm run type-check`
- **Missing dependencies**: Run `npm install`
- **Terser not found**: Install with `npm install terser`

### Docker Issues

- **Container won't start**: Check logs with `docker-compose logs frontend`
- **Port conflict**: Change port in `docker-compose.yml` (default: 80)
- **Image build fails**: Try `docker-compose build --no-cache`

## Contributing

When adding new features:

1. Add TypeScript types in `src/types/`
2. Create service methods in `src/services/`
3. Create custom hooks in `src/hooks/`
4. Build UI components in `src/components/`
5. Add pages in `src/pages/`
6. Update routing in `src/App.tsx`

## License

Same as parent project.
