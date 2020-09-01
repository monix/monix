let
  # Override the java version of sbt package
  config = {
    packageOverrides = pkgs: rec {
      sbt = pkgs.sbt.overrideAttrs (
        old: rec {
          version = "1.3.13";

          patchPhase = ''
            echo -java-home ${pkgs.openjdk11} >> conf/sbtopts
          '';
        }
      );
    };
  };

  nixpkgs = fetchTarball {
    url    = "https://github.com/NixOS/nixpkgs-channels/archive/28fce082c8ca1a8fb3dfac5c938829e51fb314c8.tar.gz";
    sha256 = "1pzmqgby1g9ypdn6wgxmbhp6hr55dhhrccn67knrpy93vib9wf8r";
  };
  pkgs = import nixpkgs { inherit config; };
in
  pkgs.mkShell {
    buildInputs = with pkgs; [
      openjdk11 # 11.0.6-internal
      sbt # 1.3.13
    ];
  }
