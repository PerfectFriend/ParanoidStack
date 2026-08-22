#!/bin/bash
# NexusChat Backup Script
# Creates a timestamped backup of the project

set -e

TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
BACKUP_DIR="backups"
BACKUP_NAME="nexuschat_backup_${TIMESTAMP}"

mkdir -p "${BACKUP_DIR}"

echo "Creating backup: ${BACKUP_NAME}"

# Create tar.gz excluding build artifacts
tar -czf "${BACKUP_DIR}/${BACKUP_NAME}.tar.gz" \
  --exclude='app/build' \
  --exclude='.gradle' \
  --exclude='.idea' \
  --exclude='*.iml' \
  --exclude='build' \
  --exclude='.git' \
  --exclude='*.apk' \
  --exclude='*.aab' \
  --exclude='backups' \
  --exclude='fdroid' \
  --exclude='*.log' \
  --exclude='*.tmp' \
  .

echo "Backup created: ${BACKUP_DIR}/${BACKUP_NAME}.tar.gz"
ls -lh "${BACKUP_DIR}/${BACKUP_NAME}.tar.gz"

# Also create a git bundle for complete history
git bundle create "${BACKUP_DIR}/${BACKUP_NAME}.bundle" --all
echo "Git bundle created: ${BACKUP_DIR}/${BACKUP_NAME}.bundle"