class Jsql < Formula
  desc "Execute SQL on json data"
  homepage "https://github.com/wlezzar/jsql"
  bottle :unneeded
  version "0.3.0"
  
  url "https://github.com/wlezzar/jsql/releases/download/0.3.0/jsql.zip"
  sha256 "c36557acd79babd2681c0ebdc0bb519f40320c1b688b6bd96f19258081c9d3be"

  def install
    bin.install "bin/jsql"
    prefix.install "lib"
  end
end
