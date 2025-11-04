# How to Push This Project to GitHub

Follow these step-by-step instructions to upload your project to GitHub.

## Prerequisites

- A GitHub account (sign up at https://github.com if you don't have one)
- Git installed on your computer (check with `git --version`)

## Step-by-Step Guide

### Step 1: Create a New Repository on GitHub

1. Go to https://github.com and sign in
2. Click the **"+"** icon in the top right corner
3. Select **"New repository"**
4. Fill in the repository details:
   - **Repository name**: `kmp-ai-chat` (or any name you prefer)
   - **Description**: "Kotlin Multiplatform AI Chat Application with Gemini Integration"
   - **Visibility**: Choose **Public** (recommended) or **Private**
   - **⚠️ DO NOT** check "Initialize with README" (we already have one)
   - **⚠️ DO NOT** add .gitignore or license (we already have them)
5. Click **"Create repository"**

### Step 2: Initialize Git in Your Project (if not already done)

Open Terminal and navigate to your project:

```bash
cd /Users/anhelina.sudenkova/AICourse/week1
```

Check if git is already initialized:

```bash
git status
```

If you see "fatal: not a git repository", initialize git:

```bash
git init
```

### Step 3: Add All Files to Git

```bash
# Add all files (except those in .gitignore)
git add .

# Verify what will be committed
git status
```

You should see files like:
- README.md
- build.gradle.kts
- src/ files
- etc.

**⚠️ Important**: Make sure `.env` files are NOT included (they should be in .gitignore)

### Step 4: Create Your First Commit

```bash
git commit -m "Initial commit: KMP AI Chat application

- Full-stack Kotlin Multiplatform application
- Compose for Web frontend
- Ktor backend with Gemini AI integration
- PostgreSQL database with Flyway migrations
- Tool calling (calculator, time, search)
- Conversation management and persistence"
```

### Step 5: Connect to GitHub Repository

Replace `YOUR_USERNAME` and `REPOSITORY_NAME` with your actual GitHub username and repository name:

```bash
# Add GitHub repository as remote
git remote add origin https://github.com/YOUR_USERNAME/REPOSITORY_NAME.git

# Verify remote was added
git remote -v
```

**Example:**
```bash
git remote add origin https://github.com/john-doe/kmp-ai-chat.git
```

### Step 6: Push to GitHub

```bash
# Push to GitHub (first time)
git branch -M main
git push -u origin main
```

You'll be prompted for your GitHub credentials:
- **Username**: Your GitHub username
- **Password**: Use a **Personal Access Token** (not your password)

**How to create a Personal Access Token:**
1. Go to https://github.com/settings/tokens
2. Click **"Generate new token"** → **"Generate new token (classic)"**
3. Give it a name: "KMP AI Chat"
4. Select scopes: Check **"repo"** (full control of private repositories)
5. Click **"Generate token"**
6. **Copy the token** (you won't see it again!)
7. Use this token as your password when pushing

### Step 7: Verify Upload

1. Go to your GitHub repository page
2. You should see all your files
3. The README.md should be displayed on the main page

## Alternative: Using SSH (Recommended for Frequent Pushes)

If you prefer SSH (more secure, no password needed):

### Step 1: Generate SSH Key (if you don't have one)

```bash
ssh-keygen -t ed25519 -C "your_email@example.com"
# Press Enter to accept default location
# Optionally set a passphrase
```

### Step 2: Add SSH Key to GitHub

```bash
# Copy your public key
cat ~/.ssh/id_ed25519.pub
```

1. Go to https://github.com/settings/keys
2. Click **"New SSH key"**
3. Paste your public key
4. Click **"Add SSH key"**

### Step 3: Use SSH URL for Remote

```bash
# Remove HTTPS remote
git remote remove origin

# Add SSH remote (replace with your username/repo)
git remote add origin git@github.com:YOUR_USERNAME/REPOSITORY_NAME.git

# Push using SSH
git push -u origin main
```

## Updating Your Repository

After making changes to your code:

```bash
# Stage all changes
git add .

# Commit with a descriptive message
git commit -m "Add feature: dark mode persistence"

# Push to GitHub
git push
```

## Best Practices

### ✅ DO Commit:
- Source code files (.kt, .kts)
- Configuration files (build.gradle.kts, docker-compose.yml)
- Documentation (README.md)
- Database migrations
- .gitignore

### ❌ DO NOT Commit:
- `.env` files (contain API keys)
- Build artifacts (`build/` folder)
- IDE files (`.idea/`, `*.iml`)
- Node modules
- Log files
- Personal credentials

### Commit Message Guidelines

Use clear, descriptive commit messages:

```bash
# Good commit messages
git commit -m "Add dark mode theme persistence"
git commit -m "Fix message duplication bug"
git commit -m "Update Gemini API timeout to 60 seconds"

# Bad commit messages
git commit -m "fix"
git commit -m "changes"
git commit -m "asdf"
```

## Adding a License

If you want to add a license:

```bash
# Create LICENSE file (MIT License example)
cat > LICENSE << 'EOF'
MIT License

Copyright (c) 2025 Your Name

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
EOF

git add LICENSE
git commit -m "Add MIT License"
git push
```

## Setting Up GitHub Actions (Optional)

If you want CI/CD, create `.github/workflows/ci.yml`:

```yaml
name: CI

on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]

jobs:
  build:
    runs-on: ubuntu-latest
    
    steps:
    - uses: actions/checkout@v3
    - uses: actions/setup-java@v3
      with:
        java-version: '21'
        distribution: 'temurin'
    
    - name: Build
      run: ./gradlew build
    
    - name: Test
      run: ./gradlew test
```

## Troubleshooting

### "Permission denied" error

- Make sure you're using the correct GitHub username
- Use a Personal Access Token instead of password
- Or set up SSH keys

### "Remote origin already exists"

```bash
# Remove existing remote
git remote remove origin

# Add new remote
git remote add origin https://github.com/YOUR_USERNAME/REPOSITORY_NAME.git
```

### "Large files" error

If you get an error about large files:

```bash
# Check for large files
find . -size +50M -not -path "./.git/*"

# Remove large files from git history if needed
git rm --cached large-file.jar
git commit -m "Remove large file"
```

### "Branch diverged" error

```bash
# Pull and merge
git pull origin main --rebase

# Or force push (⚠️ only if you're sure)
git push -f origin main
```

## Quick Reference Commands

```bash
# Initialize repository
git init

# Add files
git add .

# Commit
git commit -m "Your message"

# Add remote
git remote add origin https://github.com/USERNAME/REPO.git

# Push
git push -u origin main

# Check status
git status

# View commits
git log

# View remote
git remote -v
```

## Next Steps After Pushing

1. **Add topics/tags** on GitHub repository page for better discoverability
2. **Add a description** and website URL if you deploy it
3. **Enable GitHub Pages** if you want to host the web app
4. **Add collaborators** if working with a team
5. **Create issues** for bugs or features
6. **Add a GitHub Actions workflow** for CI/CD

---

**🎉 Congratulations!** Your project is now on GitHub!

