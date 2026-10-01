#!/bin/bash
set -e

FILE_PATH=$1
TAG_NAME=$2

if [ -z "$FILE_PATH" ] || [ -z "$TAG_NAME" ]; then
  echo "Usage: $0 <path_to_markdown_file> <tag_name>"
  exit 1
fi

if [ ! -f "$FILE_PATH" ]; then
  echo "Error: File '$FILE_PATH' does not exist."
  exit 1
fi

if [ -z "$GH_TOKEN" ]; then
  if [ -n "$GITHUB_TOKEN" ]; then
    export GH_TOKEN=$GITHUB_TOKEN
  else
    echo "Error: GH_TOKEN or GITHUB_TOKEN environment variable is not set."
    exit 1
  fi
fi

# Extract repo from git remote
REPO_URL=$(git remote get-url origin)
# Handle both https://github.com/owner/repo.git and git@github.com:owner/repo.git
REPO=$(echo "$REPO_URL" | sed -E 's/.*github.com[:\/](.*)\.git/\1/')

if [ -z "$REPO" ]; then
  echo "Error: Could not extract repository owner/name from git remote."
  exit 1
fi

echo "Publishing to repository: $REPO"
echo "Tag: $TAG_NAME"

# Parse markdown to JSON string safely using jq
BODY=$(jq -Rs . < "$FILE_PATH")

# Check if release exists
RELEASE_INFO=$(curl -s -L \
  -H "Accept: application/vnd.github+json" \
  -H "Authorization: Bearer $GH_TOKEN" \
  -H "X-GitHub-Api-Version: 2022-11-28" \
  "https://api.github.com/repos/$REPO/releases/tags/$TAG_NAME")

RELEASE_ID=$(echo "$RELEASE_INFO" | jq -r '.id // empty')

if [ -n "$RELEASE_ID" ]; then
  echo "Release $TAG_NAME already exists (ID: $RELEASE_ID). Updating..."
  curl -s -L -X PATCH \
    -H "Accept: application/vnd.github+json" \
    -H "Authorization: Bearer $GH_TOKEN" \
    -H "X-GitHub-Api-Version: 2022-11-28" \
    "https://api.github.com/repos/$REPO/releases/$RELEASE_ID" \
    -d "{\"body\":$BODY}" | jq '{id, html_url, tag_name}'
else
  echo "Release $TAG_NAME does not exist. Creating..."
  curl -s -L -X POST \
    -H "Accept: application/vnd.github+json" \
    -H "Authorization: Bearer $GH_TOKEN" \
    -H "X-GitHub-Api-Version: 2022-11-28" \
    "https://api.github.com/repos/$REPO/releases" \
    -d "{\"tag_name\":\"$TAG_NAME\",\"name\":\"$TAG_NAME\",\"body\":$BODY}" | jq '{id, html_url, tag_name}'
fi
