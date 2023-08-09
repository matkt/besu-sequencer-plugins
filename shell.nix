{ pkgs ? import <nixpkgs> {} }:

pkgs.mkShell {
  buildInputs = [
    pkgs.java-language-server pkgs.jdk17 pkgs.gradle_7 pkgs.solc pkgs.pre-commit
  ];
}