import React from "react";
import { AdminMenu } from "../components/AdminMenu";
import { Card, Form, Cascader, Checkbox } from "antd";
import { RepositoryPage } from "./RepositoryPage";


export const RepoDeployConfPage:React.FC = () => {

    const options = [
        {
            value: "Design",
            label: "Design",
        }
    ]
    return (
        <div>
            <div style={{ display: "flex", flexDirection: "row" }}>
                <AdminMenu />
                <RepositoryPage />

                <Card bordered={true}
                    style={{
                        width: 500, margin: 20
                    }}>
                    <Form >
                        <Form.Item
                            label={
                                <span>
                                    Use design repository &nbsp;
                                </span>
                            }
                        >
                            <Checkbox/>
                        </Form.Item>
                        <Form.Item
                            label={
                                <span>
                                    Repository &nbsp;
                                </span>
                            }
                        >
                            <Cascader options={options} placeholder="Design" />
                        </Form.Item>
                    </Form>
                </Card>
            </div>
        </div>
    )
};