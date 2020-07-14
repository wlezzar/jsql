class Jsql < Formula
  desc "Execute SQL on json data"
  homepage "https://github.com/wlezzar/jsql"
  bottle :unneeded
  version "0.1.0"
  
  url "todo"
  sha256 "todo"

  def install
    bin.install "bin/jsql"
    prefix.install "lib"
  end
end
