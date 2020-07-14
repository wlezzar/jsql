class Jsql < Formula
  desc "Execute SQL on json data"
  homepage "https://github.com/wlezzar/jsql"
  bottle :unneeded
  version "0.1"
  
  url "https://github.com/wlezzar/jsql/releases/download/0.1/jsql.zip"
  sha256 "d89d9b600fc043718a177197f77a82693abe5b7c34a12077906894990df803da"

  def install
    bin.install "bin/jsql"
    prefix.install "lib"
  end
end
