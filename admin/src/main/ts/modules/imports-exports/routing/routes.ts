import { Routes } from '@angular/router'
import { ImportsExportsRoot, ImportCSV } from '../components'


export let routes : Routes = [
     { 
        path: '', component: ImportsExportsRoot, 
        children: [
            { path: 'import-csv', component: ImportCSV }
        ]
     }
   
]