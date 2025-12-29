# Deploying MR File Fixit to Vercel

This guide will walk you through deploying your React TypeScript application to Vercel.

## Prerequisites

- A Vercel account (sign up at [vercel.com](https://vercel.com))
- GitHub, GitLab, or Bitbucket repository with your project
- Node.js 18+ installed locally

## Pre-Deployment Setup

### 1. Update Build Configuration

Your project uses Vite with React and TypeScript. The current build command is already configured correctly:

- **Build Command**: `tsc && vite build`
- **Output Directory**: `dist`
- **Install Command**: `npm install`

### 2. Environment Variables (If Required)

If your application uses environment variables, you'll need to set them up in Vercel:

1. Go to your project dashboard in Vercel
2. Navigate to Settings → Environment Variables
3. Add the following variables if your app uses them:

```
VITE_API_KEY=your_api_key_here
VITE_GEMINI_API_KEY=your_gemini_api_key_here
```

**Note**: All environment variables for Vite apps must be prefixed with `VITE_` to be accessible in the client-side code.

### 3. Update Vercel Configuration (Optional)

Create a `vercel.json` file in your project root for custom configuration:

```json
{
  "version": 2,
  "builds": [
    {
      "src": "package.json",
      "use": "@vercel/static-build",
      "config": {
        "distDir": "dist"
      }
    }
  ],
  "routes": [
    {
      "src": "/(.*)",
      "dest": "/index.html"
    }
  ]
}
```

This configuration ensures that all routes serve the React app for proper client-side routing.

## Deployment Methods

### Method 1: Deploy via Vercel Dashboard (Recommended for beginners)

1. **Push to Git Repository**
   ```bash
   git add .
   git commit -m "Ready for deployment"
   git push origin main
   ```

2. **Import Project in Vercel**
   - Go to [vercel.com](https://vercel.com) and sign in
   - Click "New Project"
   - Import your Git repository
   - Configure the project:
     - Framework Preset: `Vite`
     - Build Command: `tsc && vite build` (pre-filled)
     - Output Directory: `dist` (pre-filled)
     - Install Command: `npm install` (pre-filled)

3. **Deploy**
   - Click "Deploy"
   - Wait for the build to complete
   - Your app will be live at `https://your-project-name.vercel.app`

### Method 2: Deploy via Vercel CLI

1. **Install Vercel CLI**
   ```bash
   npm i -g vercel
   ```

2. **Login to Vercel**
   ```bash
   vercel login
   ```

3. **Deploy**
   ```bash
   vercel --prod
   ```

4. **Follow the prompts**
   - Link to existing project or create new
   - Confirm settings
   - Deploy

### Method 3: Deploy via GitHub Integration

1. **Push to GitHub**
   ```bash
   git init
   git add .
   git commit -m "Initial commit"
   git branch -M main
   git remote add origin https://github.com/yourusername/your-repo.git
   git push -u origin main
   ```

2. **Connect GitHub to Vercel**
   - In Vercel dashboard, go to "New Project"
   - Click "Import Git Repository"
   - Authorize Vercel to access your GitHub account
   - Select your repository
   - Configure settings and deploy

## Post-Deployment

### 1. Custom Domain (Optional)

1. Go to your project settings in Vercel
2. Navigate to "Domains"
3. Add your custom domain
4. Configure DNS records as instructed

### 2. Automatic Deployments

Vercel automatically deploys when you push to your connected Git repository:

- **Production deployments**: Push to `main` branch
- **Preview deployments**: Push to feature branches
- **Development deployments**: Push to development branches

### 3. Build Optimization

Your project is already optimized for Vercel:

- **Bundle Size**: Vite automatically tree-shakes unused code
- **Code Splitting**: Vite handles code splitting automatically
- **Sourcemaps**: Enabled in your config for better debugging

## Troubleshooting

### Build Failures

**Common Issues:**

1. **TypeScript Errors**
   - Ensure all type errors are resolved before deployment
   - Run `npm run build` locally to test

2. **Missing Dependencies**
   - Check that all dependencies are listed in `package.json`
   - Ensure you're using compatible versions

3. **Import/Export Issues**
   - Verify all imports use correct file extensions
   - Check for circular dependencies

### Runtime Errors

1. **Environment Variables**
   - Ensure all required env vars are set in Vercel dashboard
   - Verify variables are prefixed with `VITE_`

2. **API Routes**
   - Vercel supports serverless functions if you need API endpoints
   - Create `api/` directory with serverless functions

### Performance Issues

1. **Large Bundle Size**
   - Use Vite's build analyzer
   - Consider lazy loading for heavy components
   - Optimize images and assets

2. **Slow Loading**
   - Enable Vercel's Edge Network
   - Use Vercel's Image Optimization
   - Implement proper caching strategies

## Advanced Configuration

### Edge Functions (Optional)

If you need server-side functionality, create `api/hello.ts`:

```typescript
export default function handler(req, res) {
  res.status(200).json({ message: 'Hello from Vercel Edge!' });
}
```

### Redirects and Rewrites

Add to `vercel.json`:

```json
{
  "rewrites": [
    { "source": "/api/(.*)", "destination": "/api/$1" }
  ],
  "redirects": [
    { "source": "/old-page", "destination": "/new-page", "permanent": true }
  ]
}
```

### Headers for Security

```json
{
  "headers": [
    {
      "source": "/(.*)",
      "headers": [
        {
          "key": "X-Content-Type-Options",
          "value": "nosniff"
        }
      ]
    }
  ]
}
```

## Monitoring and Analytics

1. **Vercel Analytics**
   - Enable in project settings for performance insights
   - Monitor Core Web Vitals and user metrics

2. **Function Logs**
   - View logs in Vercel dashboard under "Functions" tab
   - Debug issues with detailed error traces

## Support

- **Vercel Documentation**: [vercel.com/docs](https://vercel.com/docs)
- **Vite Documentation**: [vitejs.dev](https://vitejs.dev)
- **React Documentation**: [react.dev](https://react.dev)

## Quick Reference

```bash
# Local development
npm run dev

# Build locally
npm run build

# Preview build locally
npm run preview

# Deploy with CLI
vercel --prod
```

Your MR File Fixit application should now be successfully deployed to Vercel! 🎉