# JH GetWay

লোকাল Android AI Gateway — একাধিক AI provider ও API key-এর মধ্যে routing/fallback করার জন্য নতুন project।

## বর্তমান অবস্থা

- Kotlin + Jetpack Compose ভিত্তি তৈরি
- Basic Home screen
- Gateway on/off UI placeholder
- Ktor server dependencies যোগ করা
- GitHub Actions debug APK build configuration যোগ করা

## পরবর্তী ধাপ

1. Foreground Service এবং বাস্তব Ktor server
2. `/v1/chat/completions` endpoint
3. Provider/key data model ও encrypted storage
4. Automatic fallback এবং cooldown

এই project সম্পূর্ণ আলাদা; `source_reference` project এতে ব্যবহার বা পরিবর্তন করা হয়নি।

## Copyright & license

Copyright © 2026 JH Soft Corporation. All Rights Reserved.

JH GetWay is proprietary source-available software. Public source visibility does not grant general permission to modify, fork, redistribute, rebrand, sublicense, sell, or commercially exploit the project. See [LICENSE.md](LICENSE.md), [COPYRIGHT.md](COPYRIGHT.md), and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## Implementation roadmap

See [docs/IMPLEMENTATION_ROADMAP.md](docs/IMPLEMENTATION_ROADMAP.md) for the ordered production work and acceptance sequence.
