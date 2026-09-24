# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
"""What the audit summary reports about a repository.

Both numbers checked here were wrong in a way that made the summary
disagree with the audit it was summarising, and neither had any coverage:
the aggregator had no tests at all, because it is a script the PGE shells
out to rather than a module anything imports.
"""

import importlib.machinery
import importlib.util
import sys
import types
from pathlib import Path

import pytest

AGGREGATOR = (Path(__file__).resolve().parents[2]
              / "main" / "resources" / "bin" / "rat_aggregator"
              / "rat_aggregator.py")


def load():
    """Load the aggregator by path; it ships as a script, not a package.

    `requests` is stubbed rather than installed: nothing under test makes a
    request, and a unit test for two pure functions should not need an HTTP
    library present to run.
    """
    sys.modules.setdefault("requests", types.ModuleType("requests"))
    loader = importlib.machinery.SourceFileLoader("rat_aggregator",
                                                  str(AGGREGATOR))
    spec = importlib.util.spec_from_loader("rat_aggregator", loader)
    module = importlib.util.module_from_spec(spec)
    loader.exec_module(module)
    return module


agg = load()


class TestTheLicenceBucketsPartition:

    def test_apache_is_not_also_counted_as_a_standard(self):
        # The bug. Standards meant "not Notes, Binaries, Archives or
        # Generated", so Apache fell into it as well and every
        # Apache-licensed file was counted in two bars at once.
        totals = agg.license_totals({"Apache": 10})
        assert totals["Apache"] == 10
        assert totals["Standards"] == 0

    def test_unknown_is_not_also_counted_as_a_standard(self):
        totals = agg.license_totals({"Unknown": 7})
        assert totals["Unknown"] == 7
        assert totals["Standards"] == 0

    def test_a_real_third_party_licence_is_a_standard(self):
        # Standards has to keep meaning something: a licence with no
        # aggregate field of its own still belongs there.
        totals = agg.license_totals({"MIT": 3, "BSD": 2})
        assert totals["Standards"] == 5

    def test_every_file_is_counted_exactly_once(self):
        counts = {"Apache": 10, "Unknown": 7, "Notes": 2, "Binaries": 1,
                  "Archives": 1, "Generated": 4, "MIT": 3, "BSD": 2}
        totals = agg.license_totals(counts)
        assert sum(totals.values()) == sum(counts.values()) == 30

    def test_an_all_apache_repository_does_not_double_its_own_size(self):
        # What this looked like in practice on a repository that is almost
        # entirely Apache licensed.
        totals = agg.license_totals({"Apache": 402})
        assert sum(totals.values()) == 402

    def test_a_missing_category_reports_zero_rather_than_failing(self):
        totals = agg.license_totals({})
        for name in agg.LICENSE_AGGREGATE_FIELDS:
            assert totals[name] == 0
        assert totals["Standards"] == 0


class TestTheFileCountComesFromTheAudit:

    def test_it_reads_what_solr_admitted(self):
        assert agg.audited_file_count({"response": {"numFound": 402}}) == 402

    def test_it_does_not_walk_the_checkout(self):
        # Walking counted build output and vendored directories the crawl
        # was told to exclude, so the summary said 17,621 files against 402
        # records. The helper the summary used for that is gone from this
        # module; if it comes back, so does the disagreement.
        assert not hasattr(agg, "count_num_files")


class TestLicenseTotalsOutput:

    def test_it_uses_the_computed_totals(self):
        totals = {
            "Notes": 1, "Binaries": 2, "Archives": 3, "Standards": 4,
            "Apache": 5, "Generated": 6, "Unknown": 7,
        }
        assert agg.license_totals_line(totals) == "1,2,3,4,5,6,7"
