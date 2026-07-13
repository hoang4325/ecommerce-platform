#!/usr/bin/env python3
"""Verify Maven test reports exist and have zero failures/errors."""

import sys
import xml.etree.ElementTree as ET
from pathlib import Path

REQUIRED_TEST_PREFIXES = [
    "CheckoutFlowIntegrationTest",
    "RefundSettlementIntegrationTest",
    "SettlementConcurrencyIntegrationTest",
]

def check_report(file: Path) -> int:
    tree = ET.parse(file)
    root = tree.getroot()
    tests = int(root.get("tests", "0"))
    failures = int(root.get("failures", "0"))
    errors = int(root.get("errors", "0"))
    skipped = int(root.get("skipped", "0"))
    name = root.get("name", file.name)

    print(f"  {name}: tests={tests}, failures={failures}, errors={errors}, skipped={skipped}")

    if failures > 0 or errors > 0:
        print(f"  FAIL: {name} has {failures} failures and {errors} errors")
        return 1

    return 0


def main():
    basedir = Path("ecommerce-platform-server/target")

    # Check surefire reports
    surefire = basedir / "surefire-reports"
    failsafe = basedir / "failsafe-reports"

    exit_code = 0

    print("=== Surefire Reports ===")
    if surefire.exists():
        for xml_file in sorted(surefire.glob("TEST-*.xml")):
            exit_code |= check_report(xml_file)
    else:
        print("  MISSING: surefire-reports directory not found")
        exit_code = 1

    print("\n=== Failsafe Reports ===")
    if failsafe.exists():
        found_prefixes = set()
        for xml_file in sorted(failsafe.glob("TEST-*.xml")):
            exit_code |= check_report(xml_file)
            for prefix in REQUIRED_TEST_PREFIXES:
                if prefix in xml_file.name:
                    found_prefixes.add(prefix)
                    break

        # Check required integration tests
        for prefix in REQUIRED_TEST_PREFIXES:
            if prefix not in found_prefixes:
                print(f"  MISSING: Required integration test '{prefix}' not found in failsafe-reports")
                exit_code = 1
    else:
        print("  MISSING: failsafe-reports directory not found")
        exit_code = 1

    if exit_code == 0:
        print("\nAll reports OK.")
    else:
        print("\nSome checks failed.")
    return exit_code


if __name__ == "__main__":
    sys.exit(main())
