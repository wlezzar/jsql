class Jsql < Formula
  desc "Execute SQL on json data"
  homepage "https://github.com/wlezzar/jsql"
  bottle :unneeded
  version "0.4.0"
  
  url "https://github.com/wlezzar/jsql/releases/download/0.4.0/jsql.zip"
  sha256 "79d26f8693f9896cc7cd26e959a63a17c02b9c8f7f9914d3cb4cb8624b6b2cf5"

  def install
    bin.install "bin/jsql"
    prefix.install "lib"
  end
end
