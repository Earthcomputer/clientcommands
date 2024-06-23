# Just uses the flake. For the nix-env addon (which is kind of dead) users, I use the direnv addon.
(builtins.getFlake ("git+file://" + toString ./.)).devShells.${builtins.currentSystem}.default
