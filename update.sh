#!/bin/bash

force_yes=false

# Store user argument to force all repo update
while :;
do
  case $1
    in
    -f|--force-yes) force_yes=true;;
    -p|--prune-remote) prune_remote=true;;
    -?*)
      printf 'WARN: Unknown option (ignored): %s\n' "$1" >&2
      ;;
    *) break
  esac
  shift
done

function update {
  printf "\n-- Updating project"
  git stash > /tmp/repoUpdate
  editedFiles=$(cat /tmp/repoUpdate)
  printf "\n"
  echo "$editedFiles"
  git checkout main
  git pull --rebase
  if [ "$prune_remote" = true ] ; then
    git remote update --prune
  fi
  git checkout -
  if [[ $editedFiles != *"No local changes to save"* ]]
  then
    git stash pop
  fi
  printf "\n"
}

# Check if the folder is a git repo
if [[ -d ".git" ]]; then
  # Update without prompt if yes forced
  if [ "$force_yes" = true ] ; then
    update
  # Otherwise prompt user asking for repo update
  else
    read -r -p "Update project? [y/n] " answer

    case $answer in
      [yY]* ) update;;

      [nN]* ) exit;;

      * )  echo "Enter Y or N, please.";;
    esac
  fi
else
  echo ".git not found"
fi