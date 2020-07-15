class Jsql < Formula
  desc "Execute SQL on json data"
  homepage "https://github.com/wlezzar/jsql"
  bottle :unneeded
  version "0.2.0"
  
  url "https://github.com/wlezzar/jsql/releases/download/0.2.0/jsql.zip"
  sha256 "15c60f3bf032f0987786dab639d2a873a5eea0d8819068172fb56b33c33aa036"

  def install
    bin.install "bin/jsql"
    prefix.install "lib"
  end
end
