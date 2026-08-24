#!/usr/bin/env bash
# https and 8443 - web.xml forces TLS on /api/*, so http://localhost:8080 will not work.
open "https://localhost:8443/globaltrade/"
