import {getComplete, resolveConfig, resolveCSS} from "./service.js";

resolveConfig().then((result) => {
  getComplete('text-', 1).then((result) => {
    console.log('complete = ', result)
  })

  resolveCSS({label: 'text-center font-bold'}).then((result) => {
    console.log('css = ', result.css)
  })
})