# Docker Installation Guide for macOS

## Quick Install (Recommended)

### Option 1: Download from Official Website (Easiest)

1. **Download Docker Desktop for Mac:**
   - Go to: https://www.docker.com/products/docker-desktop/
   - Click "Download for Mac"
   - Choose the version for your Mac:
     - **Apple Silicon (M1/M2/M3)**: Download "Docker Desktop for Mac with Apple Silicon"
     - **Intel Mac**: Download "Docker Desktop for Mac with Intel chip"

2. **Install Docker Desktop:**
   - Open the downloaded `.dmg` file
   - Drag Docker.app to your Applications folder
   - Open Docker.app from Applications (or Spotlight search)
   - Follow the setup wizard
   - You may need to enter your password to grant permissions

3. **Verify Installation:**
   ```bash
   docker --version
   docker compose version
   docker ps
   ```

### Option 2: Install via Homebrew

If you have Homebrew installed:

```bash
# Install Docker Desktop
brew install --cask docker

# Start Docker Desktop
open /Applications/Docker.app
```

Wait for Docker Desktop to start (you'll see a whale icon in the menu bar).

## Starting Docker Desktop

After installation, start Docker Desktop:

1. **From Applications:**
   - Open Applications folder
   - Double-click Docker.app
   - Wait for the Docker icon to appear in the menu bar (top right)

2. **From Terminal:**
   ```bash
   open /Applications/Docker.app
   ```

3. **Verify Docker is Running:**
   ```bash
   docker ps
   ```
   
   You should see an empty list (no error messages).

## Running Your Project

Once Docker Desktop is running, you can start your databases:

```bash
cd /Users/anhelina.sudenkova/AICourse/week1

# Start PostgreSQL and Redis
docker compose up -d

# Verify they're running
docker compose ps

# Check logs if needed
docker compose logs postgres
docker compose logs redis
```

## Troubleshooting

### Docker Desktop won't start
- Make sure you have enough disk space (at least 4GB free)
- Check System Preferences → Security & Privacy → General
- Restart your Mac if needed

### "Cannot connect to Docker daemon" error
- Make sure Docker Desktop app is running (check menu bar for whale icon)
- Try restarting Docker Desktop: Docker Desktop menu → Restart

### Permission denied errors
- Docker Desktop may need to be granted accessibility permissions
- Go to System Preferences → Security & Privacy → Privacy → Accessibility
- Add Docker.app if it's not there

### Port already in use
If you see errors about ports 5432 or 6379 being in use:

```bash
# Check what's using the ports
lsof -iTCP:5432 -sTCP:LISTEN -n -P
lsof -iTCP:6379 -sTCP:LISTEN -n -P

# Stop existing containers
docker compose down

# If needed, kill processes using those ports (be careful!)
# kill -9 <PID>
```

## Quick Commands Reference

```bash
# Start Docker Desktop
open /Applications/Docker.app

# Check if Docker is running
docker ps

# Start project databases
docker compose up -d

# Stop project databases
docker compose down

# View running containers
docker compose ps

# View logs
docker compose logs -f

# Remove everything (including volumes)
docker compose down -v
```

## Next Steps

After Docker is running and your databases are up:

1. Run database migrations:
   ```bash
   ./gradlew :server:flywayMigrate
   ```

2. Start the server:
   ```bash
   export GEMINI_API_KEY='your-key'
   ./gradlew :server:run
   ```

3. Start the web client:
   ```bash
   ./gradlew :web:jsBrowserDevelopmentRun
   ```

---

**Need Help?** Check the main README.md for complete setup instructions.

