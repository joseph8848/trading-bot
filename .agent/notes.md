# Antigravity Learning Notes
*Quick lessons for future conversations - kept short to preserve fresh thinking*

## User Preferences (kilon)
- Prefers **action over explanation** - just do it, don't ask too much
- Likes AI that **gives opinions** and collaborates rather than just following orders
- Prefers **GUI interfaces** over CLI when possible
- Values **learning mode** over restrictive limits for bots in development

## Trading Bot Lessons
- ❌ Don't limit daily trades during learning phase - bot needs data
- ❌ Don't use 60% confidence - data shows 80-90%+ performs better
- ✅ Keep confidence thresholds high (70%+) but don't limit volume
- ✅ Hybrid mode works: Java bot + Python analysis server on port 5001
- ✅ Bot learning data stored in: `bot_learning_data.json`

### Latest Improvements (2025-12-13):
- ✅ Added `candlestick_patterns.py` - Engulfing, Pin Bar, Doji, Star patterns
- ✅ Added **Golden Hours** detection (12:00-16:00 UTC = 70% forex volume)
- ✅ Pattern analysis now 20% of confluence score
- ✅ Session quality scoring with position size adjustments
- ✅ Research report at: `pocket_option_research.md`

### Key Trading Times (UTC):
- **BEST**: 12:00-16:00 UTC (London-NY overlap)
- **GOOD**: 08:00-17:00 UTC (London), 13:00-22:00 UTC (NY)
- **AVOID**: 21:00-00:00 UTC (quiet period)

---
*Last updated: 2025-12-13*
