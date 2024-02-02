package me.rerere.unocssintellij.intent.mapping

import me.rerere.unocssintellij.intent.AtomicCssGenerator

val marginMapping: HashMap<String, Map<Regex, AtomicCssGenerator>> = hashMapOf(
    "margin" to mapOf(
        // margin: {}px|{}em|{}rem|{}%
        Regex("^(\\d+(?:\\.\\d+)?(?:px|rem|%|em)?)\$") to { matchResult ->
            setOf("m-${matchResult.groupValues[1]}")
        },
        // margin: {}px {}px {}px {}px | {}em {}em {}em {}em | {}rem {}rem {}rem {}rem | {}% {}% {}% {}%
        Regex("^(\\d+(?:\\.\\d+)?(?:px|rem|%|em)?) (\\d+(?:\\.\\d+)?(?:px|rem|%|em)?) (\\d+(?:\\.\\d+)?(?:px|rem|%|em)?) (\\d+(?:\\.\\d+)?(?:px|rem|%|em)?)\$") to { matchResult ->
            setOf(
                "mt-${matchResult.groupValues[1]}",
                "mr-${matchResult.groupValues[2]}",
                "mb-${matchResult.groupValues[3]}",
                "ml-${matchResult.groupValues[4]}"
            )
        },
        // margin: {}px {}px {}px | {}em {}em {}em | {}rem {}rem {}rem | {}% {}% {}%
        Regex("^(\\d+(?:\\.\\d+)?(?:px|rem|%|em)?) (\\d+(?:\\.\\d+)?(?:px|rem|%|em)?) (\\d+(?:\\.\\d+)?(?:px|rem|%|em)?)\$") to { matchResult ->
            setOf(
                "mt-${matchResult.groupValues[1]}",
                "mr-${matchResult.groupValues[2]}",
                "mb-${matchResult.groupValues[3]}"
            )
        },
        // margin: {}px {}px | {}em {}em | {}rem {}rem | {}% {}%
        Regex("^(\\d+(?:\\.\\d+)?(?:px|rem|%|em)?) (\\d+(?:\\.\\d+)?(?:px|rem|%|em)?)\$") to { matchResult ->
            setOf(
                "my-${matchResult.groupValues[1]}",
                "mx-${matchResult.groupValues[2]}"
            )
        },
        // margin: (auto|intial|inherit|revert|unset)
        Regex("^(auto|initial|inherit|revert|rever-layer|unset)\$") to { matchResult ->
            setOf("m-${matchResult.groupValues[1]}")
        }
    ),
    "margin-top" to mapOf(
        // margin: {}px|{}em|{}rem|{}%
        Regex("^(\\d+(?:\\.\\d+)?(?:px|rem|%|em)?)\$") to { matchResult ->
            setOf("m-${matchResult.groupValues[1]}")
        },
        // margin: (auto|intial|inherit|revert|unset)
        Regex("^(auto|initial|inherit|revert|rever-layer|unset)\$") to { matchResult ->
            setOf("m-${matchResult.groupValues[1]}")
        }
    ),
    "margin-right" to mapOf(
        // margin: {}px|{}em|{}rem|{}%
        Regex("^(\\d+(?:\\.\\d+)?(?:px|rem|%|em)?)\$") to { matchResult ->
            setOf("m-${matchResult.groupValues[1]}")
        },
        // margin: (auto|intial|inherit|revert|unset)
        Regex("^(auto|initial|inherit|revert|rever-layer|unset)\$") to { matchResult ->
            setOf("m-${matchResult.groupValues[1]}")
        }
    ),
    "margin-bottom" to mapOf(
        // margin: {}px|{}em|{}rem|{}%
        Regex("^(\\d+(?:\\.\\d+)?(?:px|rem|%|em)?)\$") to { matchResult ->
            setOf("m-${matchResult.groupValues[1]}")
        },
        // margin: (auto|intial|inherit|revert|unset)
        Regex("^(auto|initial|inherit|revert|rever-layer|unset)\$") to { matchResult ->
            setOf("m-${matchResult.groupValues[1]}")
        }
    ),
    "margin-left" to mapOf(
        // margin: {}px|{}em|{}rem|{}%
        Regex("^(\\d+(?:\\.\\d+)?(?:px|rem|%|em)?)\$") to { matchResult ->
            setOf("m-${matchResult.groupValues[1]}")
        },
        // margin: (auto|intial|inherit|revert|unset)
        Regex("^(auto|initial|inherit|revert|rever-layer|unset)\$") to { matchResult ->
            setOf("m-${matchResult.groupValues[1]}")
        }
    ),
)