import http from 'axios'

export class ImportCSVService {

    private constructor() {}

    static async uploadCSV(importInfos): Promise<any> {
        return this.buildPostFormData(importInfos, 'validate');
    }

    static async getColumnsMapping(importInfos): Promise<any> {
        return this.buildPostFormData(importInfos, 'column/mapping');
    }

    static async updateColumnsMapping(importInfos, columsMapping): Promise<any> {
        return this.buildPostFormData(importInfos, 'column/mapping');
    }

    static async getClassesMapping(importInfos): Promise<any> {
        return this.buildPostFormData(importInfos, 'classes/mapping');
    }

    static async updateClassesMapping(importInfos, classesMapping): Promise<any> {
        return this.buildPostFormData(importInfos, 'classes/mapping');
    }

    private static async buildPostFormData(importInfos, apiPath): Promise<any> {
        let formData = new FormData();
        for(let key in importInfos){
            formData.append(key, importInfos[key]);
        }
        let response;
        try {
            response = await http.post('directory/wizard/' + apiPath, 
                formData, {'headers' : { 'Content-Type': 'multipart/form-data' }});
            } catch(error) {
            return error.response.data;
            }
            return response.data;
    }


}