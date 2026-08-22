Distributed Release Audit Tool (DRAT)
====

[![Build](https://github.com/chrismattmann/drat/actions/workflows/build.yml/badge.svg?branch=master)](https://github.com/chrismattmann/drat/actions/workflows/build.yml)
[![Site](https://github.com/chrismattmann/drat/actions/workflows/site.yml/badge.svg?branch=master)](https://github.com/chrismattmann/drat/actions/workflows/site.yml)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![JDK](https://img.shields.io/badge/JDK-21-orange.svg)](https://adoptium.net/)
[![Powered by Mnemosyne](https://img.shields.io/badge/powered%20by-Mnemosyne%201.11.0-6E4B8E.svg)](https://github.com/chrismattmann/mnemosyne)
[![Website](https://img.shields.io/badge/website-chrismattmann.github.io%2Fdrat-informational.svg)](https://chrismattmann.github.io/drat/)
 
A distributed, parallelized (Map Reduce) wrapper around [Apache RAT&trade;](http://creadur.apache.org/rat/) (Release Audit Tool). RAT is used to check for proper licensing in software projects. However, RAT takes a prohibitively long time to analyze large repositories of code, since it can only run on one JVM. Furthermore, RAT isn't customizable by file type or file size and provides no incremental output. This wrapper dramatically speeds up the process by leveraging [Mnemosyne](https://github.com/chrismattmann/mnemosyne) — the continuation of Apache OODT&trade;, which the ASF retired to the Attic in 2023 — to parallelize and workflow the following components:

1. Apache Solr&trade; based exploration of a CM repository (e.g., Git, SVN, etc.) and classification of that repository based on MIME type using Apache Tika&trade;.
2. A MIME partitioner that uses Apache Tika&trade; to automatically deduce and classify by file type and then partition Apache RAT&trade; jobs based on sets of 100 files per type (configurable) -- the M/R "partitioner"
3. A throttle wrapper for RAT to MIME targeted Apache&trade; RAT. -- the M/R "mapper"
4. A reducer to "combine" the produced RAT logs together into a global RAT report that can be used for stats generation. -- the M/R "reducer"

See the wiki for more information on installing and running DRAT:  
* [Installation instructions](https://github.com/chrismattmann/drat/wiki/Installation)  
* [How to run](https://github.com/chrismattmann/drat/wiki/How-to-Run)  
* [How to re-run](https://github.com/chrismattmann/drat/wiki/Re-running-DRAT)  
* [How to interact with DRAT](https://github.com/chrismattmann/drat/wiki/Interacting-with-DRAT)  
* [Vagrant setup](https://github.com/chrismattmann/drat/wiki/Vagrant)
* [Excluding files from analysis](https://github.com/chrismattmann/drat/wiki/RegEx-exclude-file)
* [Running DRAT on multiple repositories](https://github.com/chrismattmann/drat/wiki/DRAT-Sequential)
* [Running the DRAT Proteus GUI](https://github.com/chrismattmann/drat/wiki/Proteus---A-GUI-for-DRAT)

You can clone the wiki by running  
`git clone https://github.com/chrismattmann/drat.wiki.git`

Visit our new website [chrismattmann.github.io/drat](https://chrismattmann.github.io/drat/) at [Github](https://github.com/).

