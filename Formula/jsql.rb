class Jsql < Formula
  desc "Execute SQL on json data"
  homepage "https://github.com/wlezzar/jsql"
  bottle :unneeded
  version "0.5.0"
  
  url "https://github.com/wlezzar/jsql/releases/download/0.5.0/jsql.zip"
  sha256 "1d206b22919cf8bfd6eef0202ea384622a4ca93fc2a0de5404ac1237558d4a0c"

  def install
    bin.install "bin/jsql"
    prefix.install "lib"
  end
end
