package me.rerere.unocssintellij.intent.mapping

import me.rerere.unocssintellij.intent.AtomicCssGenerator

val paddingMapping: HashMap<String, Map<Regex, AtomicCssGenerator>> = hashMapOf(
    "padding" to mapOf(
        // margin: {}px|{}em|{}rem|{}%
        Regex("^(\\d+(?:\\.\\d+)?(?:px|rem|%|em)?)\$") to { matchResult ->
            setOf("p-${matchResult.groupValues[1]}")
        },
        // margin: {}px {}px {}px {}px | {}em {}em {}em {}em | {}rem {}rem {}rem {}rem | {}% {}% {}% {}%
        Regex("^(\\d+(?:\\.\\d+)?(?:px|rem|%|em)?) (\\d+(?:\\.\\d+)?(?:px|rem|%|em)?) (\\d+(?:\\.\\d+)?(?:px|rem|%|em)?) (\\d+(?:\\.\\d+)?(?:px|rem|%|em)?)\$") to { matchResult ->
            setOf(
                "pt-${matchResult.groupValues[1]}",
                "pr-${matchResult.groupValues[2]}",
                "pb-${matchResult.groupValues[3]}",
                "pl-${matchResult.groupValues[4]}"
            )
        },
        // margin: {}px {}px {}px | {}em {}em {}em | {}rem {}rem {}rem | {}% {}% {}%
        Regex("^(\\d+(?:\\.\\d+)?(?:px|rem|%|em)?) (\\d+(?:\\.\\d+)?(?:px|rem|%|em)?) (\\d+(?:\\.\\d+)?(?:px|rem|%|em)?)\$") to { matchResult ->
            setOf(
                "pt-${matchResult.groupValues[1]}",
                "pr-${matchResult.groupValues[2]}",
                "pb-${matchResult.groupValues[3]}"
            )
        },
        // margin: {}px {}px | {}em {}em | {}rem {}rem | {}% {}%
        Regex("^(\\d+(?:\\.\\d+)?(?:px|rem|%|em)?) (\\d+(?:\\.\\d+)?(?:px|rem|%|em)?)\$") to { matchResult ->
            setOf(
                "py-${matchResult.groupValues[1]}",
                "px-${matchResult.groupValues[2]}"
            )
        },
        // margin: (auto|intial|inherit|revert|unset)
        Regex("^(auto|initial|inherit|revert|rever-layer|unset)\$") to { matchResult ->
            setOf("p-${matchResult.groupValues[1]}")
        }
    ),
    "padding-top" to mapOf(
        // margin: {}px|{}em|{}rem|{}%
        Regex("^(\\d+(?:\\.\\d+)?(?:px|rem|%|em)?)\$") to { matchResult ->
            setOf("p-${matchResult.groupValues[1]}")
        },
        // margin: (auto|intial|inherit|revert|unset)
        Regex("^(auto|initial|inherit|revert|rever-layer|unset)\$") to { matchResult ->
            setOf("p-${matchResult.groupValues[1]}")
        }
    ),
    "padding-right" to mapOf(
        // margin: {}px|{}em|{}rem|{}%
        Regex("^(\\d+(?:\\.\\d+)?(?:px|rem|%|em)?)\$") to { matchResult ->
            setOf("p-${matchResult.groupValues[1]}")
        },
        // margin: (auto|intial|inherit|revert|unset)
        Regex("^(auto|initial|inherit|revert|rever-layer|unset)\$") to { matchResult ->
            setOf("p-${matchResult.groupValues[1]}")
        }
    ),
    "padding-bottom" to mapOf(
        // margin: {}px|{}em|{}rem|{}%
        Regex("^(\\d+(?:\\.\\d+)?(?:px|rem|%|em)?)\$") to { matchResult ->
            setOf("p-${matchResult.groupValues[1]}")
        },
        // margin: (auto|intial|inherit|revert|unset)
        Regex("^(auto|initial|inherit|revert|rever-layer|unset)\$") to { matchResult ->
            setOf("p-${matchResult.groupValues[1]}")
        }
    ),
    "padding-left" to mapOf(
        // margin: {}px|{}em|{}rem|{}%
        Regex("^(\\d+(?:\\.\\d+)?(?:px|rem|%|em)?)\$") to { matchResult ->
            setOf("p-${matchResult.groupValues[1]}")
        },
        // margin: (auto|intial|inherit|revert|unset)
        Regex("^(auto|initial|inherit|revert|rever-layer|unset)\$") to { matchResult ->
            setOf("p-${matchResult.groupValues[1]}")
        }
    ),
)