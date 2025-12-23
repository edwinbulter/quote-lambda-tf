# Release Strategy

This document outlines the process for creating releases in this monorepo using a trunk-based development approach with release branches.

## Table of Contents

- [Overview](#overview)
- [Deployment Environments](#deployment-environments)
- [Release Process](#release-process)
  - [1. Prepare for Release](#1-prepare-for-release)
  - [2. Create Release Branch](#2-create-release-branch)
  - [3. Update Version Numbers](#3-update-version-numbers)
  - [4. Commit Version Changes](#4-commit-version-changes)
  - [5. Create Release Tag](#5-create-release-tag)
  - [6. Push Changes](#6-push-changes)
  - [7. Deploy to Development](#7-deploy-to-development)
  - [8. Create GitHub Release](#8-create-github-release)
  - [9. Deploy to Production](#9-deploy-to-production)
  - [10. Merge Back to Main](#10-merge-back-to-main)
  - [11. Bump Version for Next Development Cycle](#11-bump-version-for-next-development-cycle)
- [Versioning Rules](#versioning-rules)
- [Best Practices](#best-practices)
- [Example: Patch Release](#example-patch-release)
- [Hotfix Process](#hotfix-process)
  - [When to Create a Hotfix](#when-to-create-a-hotfix)
  - [Hotfix Steps](#hotfix-steps)
  - [Important Notes](#important-notes)
- [Version Files](#version-files)
- [Release Notes](#release-notes)
  - [Location](#location)
  - [Format](#format)
  - [Best Practices](#best-practices-1)
  - [Example](#example)
  - [Automating Release Notes](#automating-release-notes)

## Overview

- **Trunk Branch**: `main` (always deployable)
- **Release Branches**: `release/x.y.z` (temporary, deleted after release)
- **Tags**: `vx.y.z` (immutable release points)
- **Versioning**: [Semantic Versioning 2.0.0](https://semver.org/)

## Deployment Environments

This project uses a **staged deployment approach** with two environments:

### Development Environment
- **Purpose**: Testing ground for new releases before production
- **URL**: https://d1fzgis91zws1k.cloudfront.net (dev)
- **When to deploy**: After creating release tag, before production
- **Validation**: Test all features, run smoke tests, verify no regressions
- **Rollback**: Easy to rollback if issues found

### Production Environment
- **Purpose**: Live environment for end users
- **URL**: Production URL (to be configured)
- **When to deploy**: After successful testing in development
- **Validation**: Final verification before going live
- **Rollback**: More complex, requires careful planning

### Deployment Flow

```
Release Tag Created (v1.0.0)
         ↓
Deploy to Development
         ↓
Test & Validate
         ↓
If issues found → Fix → Create new tag (v1.0.1)
         ↓
If OK → Deploy to Production
         ↓
Merge to Main
         ↓
Bump Version for Next Cycle
```

## Release Process

### 1. Prepare for Release

Ensure all changes are merged to `main` and all tests pass:

```bash
git checkout main
git pull origin main
git fetch --tags --force  # Ensure you have the latest tags from remote
cd quote-lambda-tf-backend && mvn verify && cd ..  # For backend
cd quote-lambda-tf-frontend && npm test && cd ..  # For frontend
```

### 2. Create Release Branch

Create a release branch from `main`:

```bash
git checkout -b release/1.0.0
```

### 3. Update Version Numbers

Update versions in all modified modules:

#### Frontend
```bash
cd quote-lambda-tf-frontend
npm version 1.0.0 --no-git-tag-version
cd ..
```

#### Backend
```bash
cd quote-lambda-tf-backend
mvn versions:set -DnewVersion=1.0.0
mvn versions:commit
cd ..
```

### 4. Commit Version Changes

```bash
git add .
git commit -m "chore: prepare release 1.0.0"
```

### 5. Create Release Tag

```bash
git tag -a v1.0.0 -m "Release 1.0.0"
```

### 6. Push Changes

```bash
git push origin release/1.0.0
git push origin v1.0.0
```

### 7. Deploy to Development

Deploy the release tag to the development environment for testing:

```bash
# The GitHub Actions workflow will automatically deploy the tag to development
# Monitor the deployment in GitHub Actions
# Verify the deployment at: https://d1fzgis91zws1k.cloudfront.net
```

**Testing Checklist:**
- [ ] All features work as expected
- [ ] No console errors or warnings
- [ ] Database migrations completed successfully
- [ ] API endpoints respond correctly
- [ ] Authentication/authorization works
- [ ] No performance regressions
- [ ] Mobile responsiveness verified

**If issues are found:**
1. Create a new patch release (e.g., v1.0.1)
2. Do NOT deploy to production
3. Repeat from step 1 with the new version

### 8. Create GitHub Release

1. Go to: https://github.com/yourusername/quote-lambda-tf/releases/new
2. Select tag: `v1.0.0`
3. Release title: `Release 1.0.0`
4. Add release notes (changes since last release)
5. Mark as "Latest release"
6. Publish release

### 9. Deploy to Production

After successful testing in development, deploy to production using Terraform CLI:

#### Backend Infrastructure Deployment

```bash
# Navigate to backend infrastructure directory
cd quote-lambda-tf-backend/infrastructure/

# Initialize Terraform (if not already initialized)
terraform init

# Select the production workspace
terraform workspace select default

# Review the planned changes (ALWAYS review before applying)
terraform plan -var-file="prod.tfvars"

# Apply the changes (requires confirmation)
terraform apply -var-file="prod.tfvars"

# Verify deployment
terraform output
```

#### Frontend Infrastructure Deployment

```bash
# Navigate to frontend infrastructure directory
cd quote-lambda-tf-frontend/infrastructure/

# Initialize Terraform (if not already initialized)
terraform init

# Select the production workspace
terraform workspace select default

# Review the planned changes (ALWAYS review before applying)
terraform plan

# Apply the changes (requires confirmation)
terraform apply

# Verify deployment
terraform output
```

**Important Notes:**
- Always verify you're in the correct workspace: `terraform workspace show`
- **ALWAYS review the plan output carefully before applying** - look for destructive changes
- Use `-var-file="prod.tfvars"` to ensure production configuration
- Both backend and frontend infrastructure must be deployed for a complete release
- Terraform will prompt for confirmation before applying changes
- Keep your local `.tfvars` files secure and never commit them to version control

**Pre-Production Checklist:**
- [ ] Development testing completed successfully
- [ ] All team members notified of deployment
- [ ] Rollback plan documented
- [ ] Terraform plan reviewed and approved (no unexpected deletions)
- [ ] Monitoring alerts configured
- [ ] Support team briefed on changes
- [ ] Local `.tfvars` files are up to date

### 10. Merge Back to Main

After creating the release, merge the release branch back to `main` to keep it synchronized with production.

```bash
git checkout main
git merge release/1.0.0
git push origin main
git branch -d release/1.0.0           # Delete local branch
git push origin --delete release/1.0.0 # Delete remote branch
```

**Why delete the release branch?**
- The tag `v1.0.0` preserves the exact release state (immutable)
- All changes are now in `main`
- Release branches are temporary by design
- Keeps the branch list clean and prevents confusion
- Use the tag for future reference: `git checkout v1.0.0`

**Why merge back to main?**

This step ensures that `main` reflects the exact state of production:

```
1. Start: main (1.0.0-SNAPSHOT or previous version)
   ↓
2. Create: release/1.0.0 (version changed to 1.0.0, SNAPSHOT removed)
   ↓
3. Tag: v1.0.0 (immutable release point)
   ↓
4. Merge to: main (1.0.0) ← main now matches production
```

**Key benefits:**
- **Version synchronization**: `main` has the release version (`1.0.0`), not `-SNAPSHOT`
- **Production parity**: `main` represents what's actually deployed
- **Hotfix base**: Future hotfixes branch from the correct production version
- **Complete history**: All release changes are preserved in `main`

### 11. Bump Version for Next Development Cycle

Immediately after merging, bump the version to prepare for the next release cycle:

#### Backend
```bash
cd quote-lambda-tf-backend
mvn versions:set -DnewVersion=1.1.0-SNAPSHOT
mvn versions:commit
cd ..
```

#### Frontend
```bash
cd quote-lambda-tf-frontend
npm version 1.1.0-SNAPSHOT --no-git-tag-version
cd ..
```

**Note:** npm accepts any version string, so `1.1.0-SNAPSHOT` works directly. Alternatively, manually edit `package.json` to set `"version": "1.1.0-SNAPSHOT"`.

#### Commit the version bump
```bash
git add .
git commit -m "chore: bump version to 1.1.0-SNAPSHOT for next development cycle"
git push origin main
```

**Why bump to SNAPSHOT?**
- Indicates work-in-progress for the next release
- Prevents confusion between released (`1.0.0`) and development (`1.1.0-SNAPSHOT`) versions
- Standard practice in Maven/Java projects, also useful for npm projects
- Makes it clear that `main` is now working toward version `1.1.0`

## Versioning Rules

1. **MAJOR** version for incompatible API changes
2. **MINOR** version for backward-compatible features
3. **PATCH** version for backward-compatible bug fixes

## Best Practices

1. Always create a release branch for version updates
2. Never push directly to `main` - use PRs
3. Keep release branches short-lived
4. Always test before tagging
5. Write clear release notes
6. **Keep release branches up to date with main** - Rebase regularly to ensure release branch changes stay on top of main
7. **Rebase before merging** - Always rebase the release branch onto main before merging the PR to ensure a clean history

### Keeping Release Branches in Sync with Main

During the release process, if `main` receives new commits while your release branch is active, you should rebase the release branch to keep it up to date:

```bash
# Fetch latest changes from main
git fetch origin main

# Rebase release branch onto main
git checkout release/1.0.0
git rebase origin/main

# If there are conflicts, resolve them and continue
git rebase --continue

# Force push the rebased branch (only safe for release branches)
git push origin release/1.0.0 --force-with-lease
```

**Why rebase instead of merge?**
- Keeps a clean, linear history
- Release branch changes always appear on top of main
- Easier to understand the release timeline
- Prevents unnecessary merge commits

### Before Merging Release Branch to Main

Before creating the PR to merge the release branch back to main, rebase it one final time:

```bash
# Ensure you have the latest main
git fetch origin main

# Rebase release branch onto main
git checkout release/1.0.0
git rebase origin/main

# Push the rebased branch
git push origin release/1.0.0 --force-with-lease

# Now create/update the PR to merge into main
# The PR will show a clean rebase with no merge conflicts
```

This ensures:
- Clean merge without conflicts
- Linear commit history
- Release changes clearly on top of main
- Easy to review and understand the release

## Example: Patch Release

```bash
# From main
git checkout main
git pull origin main
git fetch --tags --force
git checkout -b release/1.0.1
cd quote-lambda-tf-frontend && npm version patch --no-git-tag-version && cd ..
cd quote-lambda-tf-backend && mvn versions:set -DnewVersion=1.0.1 && mvn versions:commit && cd ..
git add .
git commit -m "chore: prepare release 1.0.1"
git tag -a v1.0.1 -m "Release 1.0.1"
git push origin release/1.0.1
git push origin v1.0.1
# Create GitHub release, then...
git checkout main
git merge release/1.0.1
git push origin main
git branch -d release/1.0.1
git push origin --delete release/1.0.1
# Bump version for next development cycle
cd quote-lambda-tf-backend && mvn versions:set -DnewVersion=1.0.2-SNAPSHOT && mvn versions:commit && cd ..
cd quote-lambda-tf-frontend && npm version 1.0.2-SNAPSHOT --no-git-tag-version && cd ..
git add .
git commit -m "chore: bump version to 1.0.2-SNAPSHOT for next development cycle"
git push origin main
```

## Hotfix Process

A hotfix is a critical bug fix for a production release that cannot wait for the next regular release cycle. Hotfixes branch from a release tag and create a new patch version.

### When to Create a Hotfix

- Critical production bug that affects users
- Security vulnerability in production
- Data loss or corruption issue
- Cannot wait for the next scheduled release

### Hotfix Steps

#### 1. Create Hotfix Branch from Tag

```bash
# Fetch latest tags
git fetch --tags --force

# Create hotfix branch from the production tag
git checkout -b hotfix/1.0.1 v1.0.0
```

#### 2. Fix the Issue

Make the necessary code changes to fix the bug:

```bash
# Make your bug fixes
# Test thoroughly
cd quote-lambda-tf-backend && mvn verify && cd ..
cd quote-lambda-tf-frontend && npm test && cd ..
```

#### 3. Update Version Numbers

Update to the next patch version:

```bash
# Frontend
cd quote-lambda-tf-frontend
npm version patch --no-git-tag-version
cd ..

# Backend
cd quote-lambda-tf-backend
mvn versions:set -DnewVersion=1.0.1
mvn versions:commit
cd ..
```

#### 4. Commit and Tag

```bash
git add .
git commit -m "chore: prepare hotfix 1.0.1 - fix critical bug"
git tag -a v1.0.1 -m "Hotfix 1.0.1 - fix critical bug"
```

#### 5. Push Changes

```bash
git push origin hotfix/1.0.1
git push origin v1.0.1
```

#### 6. Create GitHub Release

1. Go to: https://github.com/yourusername/quote-lambda-tf/releases/new
2. Select tag: `v1.0.1`
3. Release title: `Hotfix 1.0.1`
4. Add release notes explaining the critical fix
5. Mark as "Latest release"
6. Publish release

#### 7. Merge Back to Main

After deploying the hotfix, merge it back to `main`:

```bash
git checkout main
git pull origin main
git merge hotfix/1.0.1
git push origin main
```

**Handling Merge Conflicts:**

If `main` has changed significantly since the hotfix branch was created, you may encounter merge conflicts. Here are the strategies to handle this:

##### Strategy 1: Resolve Conflicts (Recommended for Small Changes)
```bash
git checkout main
git pull origin main
git merge hotfix/1.0.1
# If conflicts occur:
# 1. Open conflicted files and resolve manually
# 2. git add <resolved-files>
# 3. git commit -m "chore: merge hotfix/1.0.1 into main with conflict resolution"
git push origin main
```

##### Strategy 2: Cherry-Pick (When Hotfix is Small and Isolated)
```bash
git checkout main
git pull origin main
git log hotfix/1.0.1  # Find the commit hash of the hotfix
git cherry-pick <commit-hash>
git push origin main
```

##### Strategy 3: Rebase Hotfix onto Main (Cleaner History)
```bash
git checkout hotfix/1.0.1
git fetch origin
git rebase origin/main
# Resolve conflicts if they occur during rebase
git checkout main
git merge hotfix/1.0.1  # This should be a fast-forward merge now
git push origin main
```

##### Strategy 4: Manual Backport (When Code Structure Changed Drastically)
If the target code no longer exists or has been completely refactored:

1. **Analyze the hotfix**: Understand what the fix actually does
2. **Locate equivalent code**: Find where the same functionality exists in current `main`
3. **Apply fix manually**: Implement the same logic in the current codebase
4. **Test thoroughly**: Ensure the fix works in the new context
5. **Commit as new fix**: 
   ```bash
   git checkout main
   git pull origin main
   # Make the manual changes
   git add .
   git commit -m "fix: backport hotfix 1.0.1 changes to current main"
   git push origin main
   ```

**When to Use Each Strategy:**

- **Strategy 1**: Small conflicts, similar code structure
- **Strategy 2**: Single commit hotfix, minimal conflicts expected
- **Strategy 3**: Want clean linear history, conflicts are manageable
- **Strategy 4**: Major refactoring occurred, original code doesn't exist

**Prevention Tips:**

1. **Keep hotfixes small and focused**: Reduce the chance of conflicts
2. **Regular main merges**: Keep hotfix branches updated with main changes
3. **Document architectural changes**: Make it easier to backport fixes later
4. **Consider feature flags**: For changes that might need hotfixing later

#### 8. Delete Hotfix Branch

```bash
git branch -d hotfix/1.0.1
git push origin --delete hotfix/1.0.1
```

#### 9. Bump Version for Next Development Cycle

Update the version on `main` to the next development version:

```bash
cd quote-lambda-tf-backend
mvn versions:set -DnewVersion=1.1.0-SNAPSHOT
mvn versions:commit
cd ..

cd quote-lambda-tf-frontend
npm version 1.1.0-SNAPSHOT --no-git-tag-version
cd ..

git add .
git commit -m "chore: bump version to 1.1.0-SNAPSHOT for next development cycle"
git push origin main
```

### Important Notes

- **Hotfixes are rare**: Only use for critical production issues
- **Test thoroughly**: Hotfixes go directly to production, so testing is critical
- **Keep them small**: Only include the fix, no feature additions
- **Document the issue**: Clearly explain what was fixed and why it was critical
- **Merge to main**: Always merge hotfixes back to `main` to keep it in sync with production

## Version Files

### Frontend
- `quote-lambda-tf-frontend/package.json`
- `quote-lambda-tf-frontend/package-lock.json`

### Backend
- `quote-lambda-tf-backend/pom.xml`

## Release Notes

### Location
Create release notes in two places:
1. In the GitHub Release description when creating a release
2. In `CHANGELOG.md` at the root of the repository

### Format
Use the following template for release notes:

```markdown
## [Version] - YYYY-MM-DD

### Added
- New features and additions

### Changed
- Changes in existing functionality

### Deprecated
- Soon-to-be removed features

### Removed
- Removed features

### Fixed
- Bug fixes

### Security
- Security-related changes

### Infrastructure
- Changes to deployment or infrastructure

### Dependencies
- Updated dependencies
```

### Best Practices
1. **Be Specific**: Include issue/PR numbers (e.g., `#123`) and links to them
2. **Group by Type**: Categorize changes (Added, Changed, Fixed, etc.)
3. **Be Consistent**: Use the same format for all releases
4. **Include Impact**: Note if the change affects users, developers, or both
5. **Link to Documentation**: Reference relevant documentation updates

### Example
```markdown
## [1.2.0] - 2025-12-11

### Added
- New quote sharing feature (#123)
- Dark mode support (#124)

### Changed
- Improved quote loading performance (#130)
- Updated UI components to use new design system (#128)

### Fixed
- Fixed issue with quote likes not persisting (#131)
- Resolved mobile layout issues (#135)

### Dependencies
- Updated React to v18.2.0
- Upgraded AWS SDK to latest version
```

### Automating Release Notes
Consider using these tools to generate release notes:
- GitHub's built-in release notes generator
- `standard-version` for automated versioning and changelog generation
- `conventional-changelog` for commit message-based changelogs

To generate a changelog from commit messages:
```bash
# Install conventional-changelog
npm install -g conventional-changelog-cli

# Generate changelog
conventional-changelog -p angular -i CHANGELOG.md -s
```
